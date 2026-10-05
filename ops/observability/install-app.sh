#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
source_directory=$(cd -- "$(dirname -- "$0")" && pwd)
data_address=${1:?Usage: install-app.sh DATA_ADDRESS BUNDLE_DIRECTORY}
bundle=${2:?A verified Gradle observability bundle is required}
[[ $data_address =~ ^[a-zA-Z0-9.-]+$ ]] || { echo 'Invalid data address' >&2; exit 1; }
[[ -f $bundle/yanus-observability.jar ]] || { echo 'Missing observability bundle' >&2; exit 1; }
backup=/var/backups/yanus-observability/$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
cp -a /etc/prometheus/prometheus.yml /etc/grafana/grafana.ini "$backup/"
sha256sum /home/yanus/app/app.jar > "$backup/application.sha256"
install -d /etc/yanus /opt/yanus-observability/lib /etc/systemd/system/yanus.service.d /etc/systemd/system/prometheus.service.d /etc/systemd/system/grafana-server.service.d
install -m 0644 "$source_directory/application-observability.properties" /etc/yanus/application-observability.properties
install -m 0644 "$bundle/"*.jar /opt/yanus-observability/lib/
install -d -o yanus -g yanus -m 0750 /var/log/yanus/prod
install -d -o prometheus -g prometheus /var/lib/prometheus/node-exporter
install -m 0644 "$source_directory/prometheus.yml" /etc/prometheus/prometheus.yml
install -m 0644 "$source_directory/yanus-rules.yml" /etc/prometheus/yanus-rules.yml
printf '[{"targets":["%s:9100"],"labels":{"environment":"prod","service":"host","instance":"data-server"}}]\n' "$data_address" > /etc/prometheus/yanus-data-targets.json
printf '[{"targets":["%s:9187"],"labels":{"environment":"prod","service":"database","instance":"data-server"}}]\n' "$data_address" > /etc/prometheus/yanus-postgres-targets.json
printf 'ARGS="--web.listen-address=127.0.0.1:9100 --collector.textfile.directory=/var/lib/prometheus/node-exporter"\n' > /etc/default/prometheus-node-exporter
install -m 0644 "$source_directory/systemd/prometheus.conf" /etc/systemd/system/prometheus.service.d/yanus.conf
install -m 0644 "$source_directory/systemd/grafana.conf" /etc/systemd/system/grafana-server.service.d/yanus.conf
install -m 0644 "$source_directory/systemd/yanus-observability.conf" /etc/systemd/system/yanus.service.d/observability.conf
install -m 0755 "$source_directory/collect-log-metrics.sh" /usr/local/bin/yanus-collect-log-metrics
install -m 0644 "$source_directory/systemd/yanus-log-metrics."* /etc/systemd/system/
install -d -o grafana -g grafana /var/lib/grafana/yanus-dashboards
install -m 0644 "$source_directory/grafana/dashboards/"*.json /var/lib/grafana/yanus-dashboards/
install -m 0644 "$source_directory/grafana/datasources.yml" /etc/grafana/provisioning/datasources/yanus.yml
install -m 0644 "$source_directory/grafana/dashboards.yml" /etc/grafana/provisioning/dashboards/yanus.yml
install -m 0640 -o root -g grafana "$source_directory/grafana/grafana.ini" /etc/grafana/grafana.ini
install -m 0644 "$source_directory/nginx-grafana.conf" /etc/nginx/sites-available/yanus-grafana
ln -sfn /etc/nginx/sites-available/yanus-grafana /etc/nginx/sites-enabled/yanus-grafana
nginx -t
for secret in yanus-admin-password yanus-secret-key; do
  if [[ ! -f /etc/grafana/$secret ]]; then
    umask 027
    openssl rand -hex 32 > "/etc/grafana/$secret"
    chown root:grafana "/etc/grafana/$secret"
    chmod 0640 "/etc/grafana/$secret"
  fi
done
promtool check config /etc/prometheus/prometheus.yml
systemctl daemon-reload
systemctl enable prometheus grafana-server prometheus-node-exporter yanus-log-metrics.timer
systemctl restart prometheus-node-exporter prometheus grafana-server
systemctl reload nginx
systemctl restart yanus
systemctl start yanus-log-metrics.service yanus-log-metrics.timer
sha256sum -c "$backup/application.sha256"
echo "Backup: $backup"
