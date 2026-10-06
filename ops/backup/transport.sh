#!/usr/bin/env bash
set -euo pipefail
umask 077
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
load_config /etc/yanus-backup/config.json
exec 9>"$state/backup.lock"
flock -w 15 9 || exit 75
case ${1:-} in
  list)
    for file in "$spool"/*.json; do
      [[ -f $file ]] || continue
      id=$(basename "$file" .json)
      valid_id "$id" || exit 1
      [[ -f $spool/$id.age && ! -L $spool/$id.age && ! -L $file ]] || exit 1
      jq -c . "$file"
    done
    ;;
  archive)
    id=${2:?Backup ID required}
    valid_id "$id" && [[ -f $spool/$id.age && ! -L $spool/$id.age ]] || exit 1
    cat "$spool/$id.age"
    ;;
  ack)
    id=${2:?Backup ID required}
    sha=${3:?Checksum required}
    valid_id "$id" && [[ $sha =~ ^[a-f0-9]{64}$ ]] || exit 1
    [[ -f $spool/$id.json && -f $spool/$id.age ]] || exit 1
    [[ $(jq -r .sha256 "$spool/$id.json") == "$sha" ]] || exit 1
    [[ $(sha256sum "$spool/$id.age" | cut -d ' ' -f1) == "$sha" ]] || exit 1
    touch "$spool/$id.acked"
    current=0
    if [[ -f $state/offsite.json ]]; then current=$(jq -er .snapshot_epoch "$state/offsite.json"); fi
    snapshot=$(jq -er .snapshot_epoch "$spool/$id.json")
    if (( snapshot >= current )); then
      temporary=$(mktemp "$state/offsite.XXXXXX")
      jq --argjson receipt "$(date +%s)" '. + {receipt_epoch:$receipt}' "$spool/$id.json" > "$temporary"
      mv "$temporary" "$state/offsite.json"
    fi
    write_metrics
    latest=$(jq -er .id "$state/local.json")
    cutoff=$(($(date +%s) - retention_days * 86400))
    for marker in "$spool"/*.acked; do
      [[ -f $marker ]] || continue
      old=$(basename "$marker" .acked)
      valid_id "$old" || exit 1
      [[ $old != "$latest" && -f $spool/$old.json ]] || continue
      if (( $(jq -er .snapshot_epoch "$spool/$old.json") < cutoff )); then
        rm -- "$spool/$old.age" "$spool/$old.json" "$marker"
      fi
    done
    printf '{"status":"ACKNOWLEDGED"}\n'
    ;;
  *) exit 64 ;;
esac
