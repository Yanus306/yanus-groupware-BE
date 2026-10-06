#!/usr/bin/env bash
set -euo pipefail
umask 077
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
load_config "${1:-/etc/yanus-backup/config.json}"
exec 9>"$state/backup.lock"
flock -n 9 || { echo '{"status":"BUSY"}'; exit 75; }
started=$(date +%s)
id="$(date -u +%Y%m%dT%H%M%SZ)-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
work=$(mktemp -d "$runtime/work.XXXXXX")
phase=snapshot
snapshot_pid=
finish() {
  local code=$? previous temporary
  trap - EXIT
  if [[ -n $snapshot_pid ]] && kill -0 "$snapshot_pid" 2>/dev/null; then
    kill "$snapshot_pid" 2>/dev/null || true
    wait "$snapshot_pid" 2>/dev/null || true
  fi
  if (( code != 0 )); then
    previous='{"last_success_snapshot_epoch":0,"last_success_bytes":0}'
    if [[ -f $state/local.json ]]; then previous=$(cat "$state/local.json"); fi
    temporary=$(mktemp "$state/local.XXXXXX")
    jq --arg phase "$phase" --argjson attempt "$started" \
      '. + {last_run_success:0,attempt_epoch:$attempt,failed_phase:$phase}' <<<"$previous" > "$temporary"
    mv "$temporary" "$state/local.json"
  fi
  rm -f -- "$spool/$id.age.part"
  rm -rf -- "$work"
  write_metrics
  printf '{"status":"%s","phase":"%s"}\n' "$([[ $code == 0 ]] && echo SUCCESS || echo FAILED)" "$phase"
  exit "$code"
}
trap finish EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

coproc SNAPSHOT { runuser -u postgres -- psql -XqAt -v ON_ERROR_STOP=1 -d "$database" 2>"$work/snapshot.error"; }
# Bash sets the named coprocess PID when coproc starts.
# shellcheck disable=SC2153
snapshot_pid=$SNAPSHOT_PID
exec {snapshot_out}<&"${SNAPSHOT[0]}"
exec {snapshot_in}>&"${SNAPSHOT[1]}"
printf '%s\n' 'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;' \
  'SELECT pg_export_snapshot();' 'SELECT extract(epoch FROM transaction_timestamp())::bigint;' >&"$snapshot_in"
read -r -t 15 snapshot <&"$snapshot_out"
read -r -t 15 snapshot_epoch <&"$snapshot_out"
[[ $snapshot =~ ^[A-Fa-f0-9-]+$ && $snapshot_epoch =~ ^[0-9]+$ ]]
phase=dump
timeout --kill-after=5 120 runuser -u postgres -- pg_dump --format=custom --snapshot="$snapshot" -d "$database" > "$work/database.dump" 2>"$work/dump.error"
timeout --kill-after=5 30 runuser -u postgres -- pg_dumpall --roles-only --no-role-passwords > "$work/roles.sql" 2>"$work/roles.error"
phase=verification
cat >&"$snapshot_in" <<'SQL'
SELECT format('SELECT json_build_object(''schema'',%L,''table'',%L,''rows'',count(*)) FROM %I.%I;', n.nspname,c.relname,n.nspname,c.relname) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') ORDER BY c.relname
\gexec
SELECT 'TABLES_END';
SELECT coalesce(jsonb_agg(t ORDER BY installed_rank),'[]'::jsonb) FROM public.flyway_schema_history t;
SELECT coalesce(json_agg(json_build_object('table',conrelid::regclass::text,'name',conname,'definition',pg_get_constraintdef(oid)) ORDER BY conname),'[]'::json) FROM pg_constraint WHERE connamespace='public'::regnamespace;
SELECT coalesce(json_agg(json_build_object('schema',n.nspname,'name',c.relname,'kind',c.relkind,'owner',pg_get_userbyid(c.relowner),'acl',coalesce(c.relacl::text,'')) ORDER BY c.relname),'[]'::json) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m');
SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=current_database();
SELECT current_setting('server_version_num');
COMMIT;
\q
SQL
while read -r -t 30 row <&"$snapshot_out"; do
  [[ $row != TABLES_END ]] || break
  printf '%s\n' "$row" >> "$work/tables.jsonl"
done
[[ $row == TABLES_END ]]
read -r -t 30 history <&"$snapshot_out"
read -r -t 30 constraints <&"$snapshot_out"
read -r -t 30 ownership <&"$snapshot_out"
read -r -t 30 database_owner <&"$snapshot_out"
read -r -t 30 server_version <&"$snapshot_out"
wait "$snapshot_pid"
snapshot_pid=
jq -s --argjson history "$history" --argjson constraints "$constraints" \
  --argjson ownership "$ownership" --arg owner "$database_owner" \
  --argjson snapshot "$snapshot_epoch" --argjson version "$server_version" \
  '{tables:.,history:$history,constraints:$constraints,ownership:$ownership,database_owner:$owner,snapshot_epoch:$snapshot,server_version_num:$version}' "$work/tables.jsonl" > "$work/verification.json"
dump_sha=$(sha256sum "$work/database.dump" | cut -d ' ' -f1)
roles_sha=$(sha256sum "$work/roles.sql" | cut -d ' ' -f1)
verification_sha=$(sha256sum "$work/verification.json" | cut -d ' ' -f1)
jq -n --arg id "$id" --arg database "$database" --arg dump "$dump_sha" --arg roles "$roles_sha" --arg verification "$verification_sha" \
  '{version:1,id:$id,database:$database,files:{"database.dump":$dump,"roles.sql":$roles,"verification.json":$verification}}' > "$work/manifest.json"
phase=encrypt
tar -C "$work" -cf - database.dump roles.sql verification.json manifest.json | age -r "$recipient" -o "$spool/$id.age.part" 2>"$work/encrypt.error"
mv "$spool/$id.age.part" "$spool/$id.age"
bytes=$(stat -c %s "$spool/$id.age")
archive_sha=$(sha256sum "$spool/$id.age" | cut -d ' ' -f1)
metadata=$(mktemp "$spool/metadata.XXXXXX")
jq -n --arg id "$id" --arg sha "$archive_sha" --argjson bytes "$bytes" --argjson snapshot "$snapshot_epoch" \
  '{version:1,id:$id,sha256:$sha,bytes:$bytes,snapshot_epoch:$snapshot}' > "$metadata"
mv "$metadata" "$spool/$id.json"
temporary=$(mktemp "$state/local.XXXXXX")
jq -n --arg id "$id" --argjson snapshot "$snapshot_epoch" --argjson bytes "$bytes" --argjson attempt "$started" --argjson duration "$(($(date +%s)-started))" \
  '{id:$id,last_run_success:1,last_success_snapshot_epoch:$snapshot,last_success_bytes:$bytes,attempt_epoch:$attempt,duration_seconds:$duration}' > "$temporary"
mv "$temporary" "$state/local.json"
phase=complete
