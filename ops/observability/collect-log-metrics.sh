#!/usr/bin/env bash
set -euo pipefail
directory=/var/lib/prometheus/node-exporter
log=/var/log/yanus/prod/application.log
temporary=$(mktemp "$directory/yanus-logs.XXXXXX")
trap 'rm -f "$temporary"' EXIT
exists=0
bytes=0
modified=0
archives=0
if [[ -f "$log" ]]; then
  exists=1
  bytes=$(stat -c %s "$log")
  modified=$(stat -c %Y "$log")
fi
archives=$(find /var/log/yanus/prod -maxdepth 1 -type f -name '*.log.gz' -printf '.\n' | wc -l)
printf 'yanus_log_file_exists %s\nyanus_log_file_size_bytes %s\nyanus_log_file_modified_timestamp_seconds %s\nyanus_log_archives %s\nyanus_log_retention_days 14\nyanus_log_collection_timestamp_seconds %s\n' "$exists" "$bytes" "$modified" "$archives" "$(date +%s)" > "$temporary"
chmod 0644 "$temporary"
mv "$temporary" "$directory/yanus-logs.prom"
