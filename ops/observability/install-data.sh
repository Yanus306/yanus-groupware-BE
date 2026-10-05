#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
bind_address=${1:?Usage: install-data.sh DATA_BIND_ADDRESS APP_SOURCE_ADDRESS}
app_address=${2:?Application source address is required}
for address in "$bind_address" "$app_address"; do
  [[ $address =~ ^[0-9.]+$ ]] || { echo 'IPv4 address required' >&2; exit 1; }
done
backup=/var/backups/yanus-observability/$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
cp -a /etc/default/prometheus-node-exporter /etc/default/prometheus-postgres-exporter "$backup/"
runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='prometheus') THEN CREATE ROLE prometheus LOGIN; END IF; END \$\$;"
runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -c 'GRANT pg_monitor TO prometheus;'
runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -c 'GRANT CONNECT ON DATABASE yanus TO prometheus;'
printf 'ARGS="--web.listen-address=%s:9100"\n' "$bind_address" > /etc/default/prometheus-node-exporter
printf 'DATA_SOURCE_NAME="user=prometheus host=/run/postgresql dbname=yanus sslmode=disable"\nARGS="--web.listen-address=%s:9187 --collector.database"\n' "$bind_address" > /etc/default/prometheus-postgres-exporter
ufw allow from "$app_address" to "$bind_address" port 9100 proto tcp comment 'Yanus node metrics'
ufw allow from "$app_address" to "$bind_address" port 9187 proto tcp comment 'Yanus postgres metrics'
systemctl enable prometheus-node-exporter prometheus-postgres-exporter
systemctl restart prometheus-node-exporter prometheus-postgres-exporter
runuser -u prometheus -- psql -X -h /run/postgresql -d yanus -Atc 'SELECT current_user;'
echo "Backup: $backup"
