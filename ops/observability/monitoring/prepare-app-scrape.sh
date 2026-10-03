#!/usr/bin/env bash
set -euo pipefail
app=${1:?APP_PRIVATE_IP required}
monitor=${2:?MONITORING_PRIVATE_IP required}
for value in "$app" "$monitor"; do [[ $value =~ ^[0-9.]+$ ]]; done
backup=/var/backups/yanus-observability/monitoring-migration
install -d -m 0700 "$backup"
if [[ ! -f $backup/node-exporter.default ]]; then
  cp -a /etc/default/prometheus-node-exporter "$backup/node-exporter.default"
fi
ufw allow from "$monitor" to "$app" port 9100 proto tcp
ufw allow from "$monitor" to "$app" port 9092 proto tcp
printf 'ARGS="--web.listen-address=0.0.0.0:9100 --collector.textfile.directory=/var/lib/prometheus/node-exporter"\n' > /etc/default/prometheus-node-exporter
printf 'server {\n listen %s:9092;\n server_name _;\n allow %s;\n deny all;\n access_log off;\n location = /actuator/prometheus { limit_except GET { deny all; } proxy_pass http://127.0.0.1:9091; }\n location / { return 404; }\n}\n' "$app" "$monitor" > /etc/nginx/sites-available/yanus-scrape
ln -sfn /etc/nginx/sites-available/yanus-scrape /etc/nginx/sites-enabled/yanus-scrape
nginx -t
systemctl restart prometheus-node-exporter
systemctl reload nginx
