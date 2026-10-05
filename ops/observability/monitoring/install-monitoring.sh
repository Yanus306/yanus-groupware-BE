#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
source_directory=$(cd -- "$(dirname -- "$0")/.." && pwd)
app=${1:?APP_PRIVATE_IP required}
data=${2:?DATA_PRIVATE_IP required}
address=${3:?MONITORING_PRIVATE_IP required}
for value in "$app" "$data" "$address"; do
  [[ $value =~ ^[0-9.]+$ ]] || { echo 'IPv4 required' >&2; exit 1; }
done
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg unzip ufw nginx apache2-utils prometheus prometheus-node-exporter
ufw allow 22/tcp
ufw allow from "$app" to "$address" port 3000 proto tcp
ufw allow from "$app" to "$address" port 3101 proto tcp
ufw --force enable
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://apt.grafana.com/gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/grafana.gpg
printf 'deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main\n' > /etc/apt/sources.list.d/grafana.list
apt-get update
apt-get install -y grafana=13.2.3
systemctl stop grafana-server prometheus
install -d /etc/prometheus /etc/loki /etc/grafana/provisioning/datasources /etc/grafana/provisioning/dashboards /etc/systemd/system/prometheus.service.d /etc/systemd/system/grafana-server.service.d
id loki >/dev/null 2>&1 || useradd --system --home /var/lib/loki --shell /usr/sbin/nologin loki
install -d -o loki -g loki -m 0750 /var/lib/loki
install -d -o grafana -g grafana /var/lib/grafana/yanus-dashboards
install -m 0644 "$source_directory/monitoring/prometheus.yml" /etc/prometheus/prometheus.yml
install -m 0644 "$source_directory/yanus-rules.yml" /etc/prometheus/yanus-rules.yml
printf '[{"targets":["%s:9092"],"labels":{"environment":"prod","service":"backend","instance":"app-server"}}]\n' "$app" > /etc/prometheus/yanus-backend-targets.json
printf '[{"targets":["%s:9100"],"labels":{"environment":"prod","service":"host","instance":"app-server"}},{"targets":["%s:9100"],"labels":{"environment":"prod","service":"host","instance":"data-server"}}]\n' "$app" "$data" > /etc/prometheus/yanus-host-targets.json
printf '[{"targets":["%s:9187"],"labels":{"environment":"prod","service":"database","instance":"data-server"}}]\n' "$data" > /etc/prometheus/yanus-postgres-targets.json
printf 'ARGS="--web.listen-address=127.0.0.1:9100"\n' > /etc/default/prometheus-node-exporter
install -m 0644 "$source_directory/monitoring/prometheus.conf" /etc/systemd/system/prometheus.service.d/yanus.conf
install -m 0644 "$source_directory/monitoring/grafana.conf" /etc/systemd/system/grafana-server.service.d/yanus.conf
install -m 0644 "$source_directory/grafana/dashboards/"*.json /var/lib/grafana/yanus-dashboards/
install -m 0644 "$source_directory/grafana/dashboards.yml" /etc/grafana/provisioning/dashboards/yanus.yml
install -m 0644 "$source_directory/grafana/datasources.yml" /etc/grafana/provisioning/datasources/yanus.yml
printf '\n  - name: Yanus Loki\n    uid: yanus-loki\n    type: loki\n    access: proxy\n    url: http://127.0.0.1:3100\n    editable: false\n    jsonData:\n      maxLines: 200\n' >> /etc/grafana/provisioning/datasources/yanus.yml
sed "s/http_addr = 127.0.0.1/http_addr = $address/" "$source_directory/grafana/grafana.ini" > /etc/grafana/grafana.ini
chown root:grafana /etc/grafana/grafana.ini
chmod 0640 /etc/grafana/grafana.ini
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
curl -fsSL https://github.com/grafana/loki/releases/download/v3.7.8/loki-linux-amd64.zip -o "$temporary/loki-linux-amd64.zip"
(cd "$temporary" && printf '62aea42c9cba52cd1642b3666ab37019a0ce4c24ab50b07e85dccc8d812f7d61  loki-linux-amd64.zip\n' | sha256sum -c -)
unzip -q "$temporary/loki-linux-amd64.zip" -d "$temporary"
install -m 0755 "$temporary/loki-linux-amd64" /usr/local/bin/loki
install -m 0644 "$source_directory/monitoring/loki.yml" /etc/loki/config.yml
install -m 0644 "$source_directory/monitoring/loki.service" /etc/systemd/system/loki.service
/usr/local/bin/loki -config.file=/etc/loki/config.yml -verify-config=true
umask 027
if [[ ! -s /etc/nginx/loki-password ]]; then
  openssl rand -hex 32 > /etc/nginx/loki-password
fi
chmod 0600 /etc/nginx/loki-password
htpasswd -ci /etc/nginx/loki.htpasswd alloy < /etc/nginx/loki-password
chown root:www-data /etc/nginx/loki.htpasswd
chmod 0640 /etc/nginx/loki.htpasswd
printf 'server {\n listen %s:3101;\n server_name _;\n allow %s;\n deny all;\n auth_basic "Yanus Loki ingestion";\n auth_basic_user_file /etc/nginx/loki.htpasswd;\n access_log off;\n location = /loki/api/v1/push { proxy_pass http://127.0.0.1:3100; proxy_set_header Authorization ""; }\n location / { return 404; }\n}\n' "$address" "$app" > /etc/nginx/sites-available/yanus-loki
ln -sfn /etc/nginx/sites-available/yanus-loki /etc/nginx/sites-enabled/yanus-loki
rm -f /etc/nginx/sites-enabled/default
nginx -t
promtool check config /etc/prometheus/prometheus.yml
systemctl daemon-reload
systemctl enable loki grafana-server prometheus prometheus-node-exporter nginx
systemctl restart loki prometheus-node-exporter
systemctl reload nginx
echo 'Ready to restore Grafana and Prometheus data; those services remain stopped'
