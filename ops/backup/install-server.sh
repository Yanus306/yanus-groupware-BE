#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $(id -u) == 0 ]] || exit 1
source_directory=$(cd -- "$(dirname -- "$0")" && pwd)
recipient=${1:?age public recipient required}
[[ $recipient =~ ^age1[0-9a-z]{58}$ ]] || exit 64
for command in age jq pg_dump pg_dumpall flock timeout; do command -v "$command" >/dev/null; done
[[ $(pg_dump --version) == *' 16.'* ]] || { echo 'PostgreSQL 16 도구가 필요합니다.' >&2; exit 1; }
install -d -m 0700 /etc/yanus-backup /var/backups/yanus-db /var/lib/yanus-backup
install -d -m 0755 /usr/local/libexec/yanus-backup /var/lib/prometheus/node-exporter /etc/systemd/system/prometheus-node-exporter.service.d
if [[ -f /etc/yanus-backup/config.json ]]; then
  [[ $(jq -r .recipient /etc/yanus-backup/config.json) == "$recipient" ]] || { echo '기존 암호화 키와 다릅니다. 키 변경 절차를 확인하세요.' >&2; exit 1; }
else
  jq --arg recipient "$recipient" '.recipient=$recipient' "$source_directory/config.example.json" > /etc/yanus-backup/config.json
  chmod 0600 /etc/yanus-backup/config.json
fi
install -m 0755 "$source_directory/common.sh" "$source_directory/backup.sh" "$source_directory/transport.sh" /usr/local/libexec/yanus-backup/
install -m 0644 "$source_directory/yanus-db-backup.service" "$source_directory/yanus-db-backup.timer" /etc/systemd/system/
install -m 0644 "$source_directory/node-exporter.conf" /etc/systemd/system/prometheus-node-exporter.service.d/yanus-backup.conf
# shellcheck source=common.sh
source /usr/local/libexec/yanus-backup/common.sh
load_config
write_metrics
systemd-analyze verify /etc/systemd/system/yanus-db-backup.service /etc/systemd/system/yanus-db-backup.timer
systemctl daemon-reload
systemctl restart prometheus-node-exporter
systemctl enable --now yanus-db-backup.timer
systemctl is-active --quiet prometheus-node-exporter yanus-db-backup.timer
echo '백업 timer와 textfile 수집을 활성화했습니다. 첫 백업과 Mac 전송은 별도로 확인하세요.'
