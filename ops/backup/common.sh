#!/usr/bin/env bash
set -euo pipefail

load_config() {
  local config=${1:-/etc/yanus-backup/config.json}
  [[ $(id -u) == 0 && -f $config && ! -L $config ]] || return 1
  [[ $(stat -c %u "$config") == 0 ]] || return 1
  [[ $(stat -c %a "$config") =~ ^(400|600)$ ]] || return 1
  jq -e '.database | test("^[a-z][a-z0-9_]{0,62}$")' "$config" >/dev/null
  database=$(jq -r .database "$config")
  spool=$(jq -r .spool "$config")
  runtime=$(jq -r .runtime "$config")
  state=$(jq -r .state "$config")
  metrics=$(jq -r .metrics "$config")
  recipient=$(jq -r .recipient "$config")
  retention_days=$(jq -er '.retention_days | select(type == "number" and . >= 1 and . <= 90 and floor == .)' "$config")
  for directory in "$spool" "$runtime" "$state" "$metrics"; do
    [[ $directory =~ ^/[a-zA-Z0-9_/-]+$ && $directory != / && $directory != *..* && ! -L $directory ]] || return 1
  done
  [[ $recipient =~ ^age1[0-9a-z]{58}$ ]] || return 1
  export database spool runtime state metrics recipient retention_days
  install -d -m 0700 "$spool" "$runtime" "$state"
  install -d -m 0755 "$metrics"
}

write_metrics() {
  local output local_at=0 offsite_at=0 local_ok=0 collection_at=0 size=0 duration=0
  output=$(mktemp "$metrics/yanus-backup.XXXXXX")
  if [[ -f $state/local.json ]]; then
    local_at=$(jq -er .last_success_snapshot_epoch "$state/local.json")
    local_ok=$(jq -er .last_run_success "$state/local.json")
    collection_at=$(jq -er .attempt_epoch "$state/local.json")
    size=$(jq -er .last_success_bytes "$state/local.json")
    duration=$(jq -er '.duration_seconds // 0' "$state/local.json")
  fi
  if [[ -f $state/offsite.json ]]; then
    offsite_at=$(jq -er .snapshot_epoch "$state/offsite.json")
  fi
  printf 'yanus_backup_local_snapshot_timestamp_seconds %s\nyanus_backup_offsite_snapshot_timestamp_seconds %s\nyanus_backup_last_run_success %s\nyanus_backup_attempt_timestamp_seconds %s\nyanus_backup_archive_bytes %s\nyanus_backup_duration_seconds %s\nyanus_backup_metrics_timestamp_seconds %s\n' \
    "$local_at" "$offsite_at" "$local_ok" "$collection_at" "$size" "$duration" "$(date +%s)" > "$output"
  chmod 0644 "$output"
  mv "$output" "$metrics/yanus-backup.prom"
}

valid_id() { [[ $1 =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{16}$ ]]; }
