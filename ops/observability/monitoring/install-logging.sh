#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
source_directory=$(cd -- "$(dirname -- "$0")/.." && pwd)
monitor=${1:?MONITORING_PRIVATE_IP required}
bundle=${2:?BUNDLE_DIRECTORY required}
password=${3:?LOKI_PASSWORD_FILE required}
[[ $monitor =~ ^[0-9.]+$ && -f $bundle/yanus-observability.jar && -f $password ]]
backup=/var/backups/yanus-observability/logging-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
cp -a /opt/yanus-observability/lib /etc/yanus/application-observability.properties /etc/nginx/sites-available/yanus "$backup/"
if [[ -f /etc/nginx/conf.d/yanus-api-log.conf ]]; then
  cp -a /etc/nginx/conf.d/yanus-api-log.conf "$backup/"
fi
rollback() {
  trap - ERR
  systemctl stop alloy || true
  cp -a "$backup/lib/." /opt/yanus-observability/lib/
  cp -a "$backup/application-observability.properties" /etc/yanus/application-observability.properties
  cp -a "$backup/yanus" /etc/nginx/sites-available/yanus
  if [[ -f $backup/yanus-api-log.conf ]]; then
    cp -a "$backup/yanus-api-log.conf" /etc/nginx/conf.d/yanus-api-log.conf
  else
    rm -f /etc/nginx/conf.d/yanus-api-log.conf
  fi
  systemctl restart yanus || true
  nginx -t && systemctl reload nginx
  echo "Deployment failed; previous configuration restored from $backup" >&2
}
sha256sum /home/yanus/app/app.jar > "$backup/application.sha256"
curl -fsS http://127.0.0.1:8080/v3/api-docs > "$backup/openapi-before.json"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y alloy acl
systemctl stop alloy
install -d /etc/alloy /etc/systemd/system/alloy.service.d
install -m 0640 -o root -g alloy "$password" /etc/alloy/loki-password
sed "s/MONITORING_ADDRESS/$monitor/g" "$source_directory/monitoring/alloy.config" > /etc/alloy/config.alloy
printf '[Service]\nMemoryMax=256M\nEnvironment=GOMEMLIMIT=192MiB\n' > /etc/systemd/system/alloy.service.d/yanus.conf
setfacl -m u:alloy:rx /var/log/yanus /var/log/yanus/prod
setfacl -m d:u:alloy:r--,d:g::r--,d:m::r-- /var/log/yanus/prod
setfacl -m u:alloy:r--,g::r--,m::r-- /var/log/yanus/prod/application.log
alloy validate /etc/alloy/config.alloy
install -m 0644 "$source_directory/monitoring/nginx-api-log.conf" /etc/nginx/conf.d/yanus-api-log.conf
python3 -c 'import pathlib; p=pathlib.Path("/etc/nginx/sites-available/yanus"); text=p.read_text(); text=text if "access_log /var/log/nginx/access.log yanus_api;" in text else text.replace("server_name api.yanus.bond;", "server_name api.yanus.bond;\n    access_log /var/log/nginx/access.log yanus_api;"); text=text if "proxy_set_header X-Request-ID $request_id;" in text else text.replace("proxy_set_header Host $host;", "proxy_set_header Host $host;\n        proxy_set_header X-Request-ID $request_id;\n        proxy_hide_header X-Request-ID;\n        add_header X-Request-ID $request_id always;"); p.write_text(text)'
nginx -t
trap rollback ERR
install -m 0644 "$bundle/"*.jar /opt/yanus-observability/lib/
install -m 0644 "$source_directory/application-observability.properties" /etc/yanus/application-observability.properties
systemctl daemon-reload
systemctl restart yanus
for attempt in $(seq 1 120); do
  if curl --max-time 2 -fsS http://127.0.0.1:9091/actuator/health >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS http://127.0.0.1:9091/actuator/health >/dev/null
systemctl reload nginx
systemctl enable --now alloy
curl -fsS http://127.0.0.1:8080/v3/api-docs > "$backup/openapi-after.json"
cmp "$backup/openapi-before.json" "$backup/openapi-after.json"
sha256sum -c "$backup/application.sha256"
trap - ERR
echo "Logging backup: $backup"
