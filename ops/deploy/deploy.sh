#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
[[ $(id -u) == 0 ]] || { echo 'Root-owned deployment helper required' >&2; exit 1; }
[[ $# == 1 && $1 =~ ^/home/yanus/incoming/[a-f0-9]{40}$ ]] || { echo 'Expected one immutable incoming commit directory' >&2; exit 1; }
exec 9>/var/lock/yanus-deploy.lock
flock -n 9 || { echo 'DEPLOYMENT_LOCKED' >&2; exit 75; }
tools=/usr/local/lib/yanus-deploy
app=/home/yanus/app
baseline=/etc/yanus-deploy/baseline.json
[[ -f $baseline && ! -L $baseline && $(stat -c %U "$baseline") == root && $(stat -c %a "$baseline") == 600 ]] || { echo 'Protected reviewed baseline required' >&2; exit 1; }
[[ -f $app/app.jar ]] || { echo 'Existing application required' >&2; exit 1; }
systemctl is-active --quiet yanus || { echo 'Existing service must be active' >&2; exit 1; }
incoming=$1
commit=${incoming##*/}
release="$app/releases/$commit-$(date -u +%Y%m%dT%H%M%SZ)-$$"
install -d -m 0755 "$app/releases" "$release"
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
for file in app.jar manifest.json; do
  [[ -f $incoming/$file && ! -L $incoming/$file ]] || { echo 'Regular candidate files required' >&2; exit 1; }
  limit=268435456
  [[ $file != manifest.json ]] || limit=1048576
  [[ $(stat -c %s "$incoming/$file") -le $limit ]] || { echo 'Candidate size limit exceeded' >&2; exit 1; }
  cp -P -- "$incoming/$file" "$release/$file"
  [[ -f $release/$file && ! -L $release/$file ]] || { echo 'Candidate changed during copy' >&2; exit 1; }
  [[ $(stat -c %s "$release/$file") -le $limit ]] || { echo 'Candidate size changed beyond limit' >&2; exit 1; }
  chown root:root "$release/$file"; chmod 0644 "$release/$file"
done
[[ $(jq -r .commit "$release/manifest.json") == "$commit" ]] || { echo 'Commit path mismatch' >&2; exit 1; }
psql 'service=yanus-deploy' -XAt --set=ON_ERROR_STOP=1 -c "SELECT coalesce(json_agg(t ORDER BY installed_rank),'[]'::json) FROM (SELECT installed_rank, version, script, checksum, type, success FROM flyway_schema_history ORDER BY installed_rank) t" > "$temporary/history.json"
node "$tools/deploy/preflight.mjs" "$release" "$app/app.jar" "$baseline" "$temporary/history.json"
previous_hash=$(sha256sum "$app/app.jar" | cut -d' ' -f1)
previous_commit=$(jq -r '.runningCommit // empty' "$baseline")
previous_mode=legacy
[[ -z $previous_commit ]] || previous_mode=candidate
node "$tools/deploy/verify-release.mjs" "$previous_commit" "$previous_mode"
snapshot="/var/backups/yanus-deploy/$(date -u +%Y%m%dT%H%M%SZ)-$$"
install -d -m 0700 "$snapshot"
for file in "$app/app.jar" "$app/.env" /etc/systemd/system/yanus.service /etc/systemd/system/yanus.service.d /opt/yanus-observability /etc/yanus-deploy/baseline.json; do
  [[ ! -e $file ]] || cp --parents -a "$file" "$snapshot"
done
cp -aL "$app/app.jar" "$snapshot/previous.jar"
[[ $(sha256sum "$snapshot/previous.jar" | cut -d' ' -f1) == "$previous_hash" ]] || { echo 'Snapshot checksum mismatch' >&2; exit 1; }
notify() {
  node "$tools/deploy/notify.mjs" "$1" "$commit" | tee "$snapshot/notification.json" || true
}
restore() {
  trap - ERR INT TERM
  set +e
  for file in /etc/systemd/system/yanus.service.d /opt/yanus-observability; do
    rm -rf -- "$file"
    [[ ! -e $snapshot$file ]] || cp -a "$snapshot$file" "$file"
  done
  cp -a "$snapshot/etc/yanus-deploy/baseline.json" "$baseline"
  for file in "$app/.env" /etc/systemd/system/yanus.service; do
    [[ ! -e $snapshot$file ]] || cp -a "$snapshot$file" "$file"
  done
  if [[ -L $snapshot$app/app.jar ]]; then
    cp -a "$snapshot$app/app.jar" "$app/.rollback-$$"
  else
    cp -a "$snapshot/previous.jar" "$app/.rollback-$$"
  fi
  mv -Tf "$app/.rollback-$$" "$app/app.jar"
  systemctl daemon-reload
  outcome=ROLLBACK_FAILED
  if timeout 30s systemctl restart yanus && [[ $(sha256sum "$app/app.jar" | cut -d' ' -f1) == "$previous_hash" ]] && node "$tools/deploy/verify-release.mjs" "$previous_commit" "$previous_mode"; then
    outcome=ROLLED_BACK
  fi
  printf '{"status":"%s","candidate":"%s","snapshot":"%s"}\n' "$outcome" "$commit" "$snapshot" | tee "$snapshot/result.json"
  notify "$outcome"
  exit 1
}
trap restore ERR INT TERM
install -d -m 0755 /etc/systemd/system/yanus.service.d
install -m 0644 "$tools/deploy/yanus-native.conf" /etc/systemd/system/yanus.service.d/zz-deployment.conf
if [[ -f /opt/yanus-observability/application-observability.properties ]]; then
  install -m 0644 "$tools/observability/application-observability.properties" /opt/yanus-observability/application-observability.properties
fi
ln -s "$release/app.jar" "$app/.activate-$$"
mv -Tf "$app/.activate-$$" "$app/app.jar"
systemctl daemon-reload
timeout 30s systemctl restart yanus
node "$tools/deploy/verify-release.mjs" "$commit"
node "$tools/deploy/verify-public.mjs"
[[ $(sha256sum "$app/app.jar" | cut -d' ' -f1) == $(jq -r .sha256 "$release/manifest.json") ]]
jq --arg commit "$commit" --arg sha "$(jq -r .sha256 "$release/manifest.json")" '.sourceCommit=$commit | .runningCommit=$commit | .sha256=$sha' "$baseline" > "$temporary/baseline.json"
install -m 0600 "$temporary/baseline.json" "$baseline"
trap - ERR INT TERM
printf '{"status":"SUCCESS","commit":"%s","snapshot":"%s"}\n' "$commit" "$snapshot" | tee "$snapshot/result.json"
notify SUCCESS
