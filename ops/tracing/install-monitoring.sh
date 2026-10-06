#!/usr/bin/env bash
set -Eeuo pipefail
umask 027
[[ $(id -u) == 0 && $# == 2 ]] || { echo 'root APP_IP MONITOR_IP required' >&2; exit 1; }
app=$1
monitor=$2
for ip in "$app" "$monitor"; do [[ $ip =~ ^192\.168\.0\.[0-9]{1,3}$ ]] || exit 1; done
source_directory=$(cd -- "$(dirname -- "$0")" && pwd)
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
backup=/var/backups/yanus-tracing/monitor-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
paths=(/usr/local/bin/tempo /etc/tempo /etc/systemd/system/tempo.service /etc/nginx/sites-available/yanus-tempo /etc/nginx/tempo.htpasswd /etc/grafana/provisioning/datasources/yanus-tracing.yml /etc/grafana/provisioning/datasources/yanus.yml)
previous_active=false
systemctl is-active --quiet tempo && previous_active=true
for path in "${paths[@]}"; do
  [[ ! -e $path ]] || cp --parents -a "$path" "$backup"
done
rollback() {
  trap - ERR INT TERM
  systemctl stop tempo || true
  for path in "${paths[@]}"; do
    rm -rf -- "$path"
    [[ ! -e $backup$path ]] || cp -a "$backup$path" "$path"
  done
  if [[ ! -e $backup/etc/nginx/sites-available/yanus-tempo ]]; then
    rm -f /etc/nginx/sites-enabled/yanus-tempo
    ufw delete allow from "$app" to "$monitor" port 4320 proto tcp || true
  fi
  systemctl daemon-reload
  if $previous_active; then systemctl restart tempo || true; else systemctl disable tempo || true; fi
  nginx -t && systemctl reload nginx
  systemctl restart grafana-server || true
  echo "TEMPO_INSTALL_FAILED: restored $backup" >&2
  exit 1
}
curl --connect-timeout 10 --max-time 120 -fsSL https://github.com/grafana/tempo/releases/download/v3.1.0/tempo_3.1.0_linux_amd64.tar.gz -o "$temporary/tempo.tar.gz"
printf '9604ab46348270a42954801bba91cd65335e16568ef886bdf71b988fdcb7ac4e  %s\n' "$temporary/tempo.tar.gz" | sha256sum -c -
tar -xzf "$temporary/tempo.tar.gz" -C "$temporary" tempo
trap rollback ERR INT TERM
id tempo >/dev/null 2>&1 || useradd --system --home /var/tempo --shell /usr/sbin/nologin tempo
install -d -o tempo -g tempo -m 0750 /var/tempo
install -d -m 0755 /etc/tempo
install -m 0755 "$temporary/tempo" /usr/local/bin/tempo
install -m 0644 "$source_directory/tempo.yml" /etc/tempo/config.yml
/usr/local/bin/tempo -config.file=/etc/tempo/config.yml -config.verify=true
install -m 0644 "$source_directory/tempo.service" /etc/systemd/system/tempo.service
if [[ ! -f /etc/tempo/ingest-password ]]; then openssl rand -hex 32 | tr -d '\n' > /etc/tempo/ingest-password; fi
chmod 0600 /etc/tempo/ingest-password
htpasswd -ci /etc/nginx/tempo.htpasswd alloy < /etc/tempo/ingest-password
chown root:www-data /etc/nginx/tempo.htpasswd
chmod 0640 /etc/nginx/tempo.htpasswd
cat > /etc/nginx/sites-available/yanus-tempo <<EOF
server {
  listen $monitor:4320;
  server_name _;
  allow $app;
  deny all;
  auth_basic "Yanus trace ingestion";
  auth_basic_user_file /etc/nginx/tempo.htpasswd;
  access_log off;
  client_max_body_size 1m;
  location = /v1/traces {
    proxy_pass http://127.0.0.1:4318;
    proxy_set_header Authorization "";
    proxy_connect_timeout 2s;
    proxy_read_timeout 5s;
  }
  location / { return 404; }
}
EOF
ln -sfn /etc/nginx/sites-available/yanus-tempo /etc/nginx/sites-enabled/yanus-tempo
nginx -t
ufw allow from "$app" to "$monitor" port 4320 proto tcp
cat > /etc/grafana/provisioning/datasources/yanus-tracing.yml <<'EOF'
apiVersion: 1
datasources:
  - name: Yanus Tempo
    uid: yanus-tempo
    type: tempo
    access: proxy
    url: http://127.0.0.1:3200
    editable: false
    jsonData:
      httpMethod: GET
      tracesToLogsV2:
        datasourceUid: yanus-loki
        spanStartTimeShift: '-5m'
        spanEndTimeShift: '5m'
        customQuery: true
        query: '{environment="prod",service="backend",instance="app-server"} | json | traceId="$${__trace.traceId}"'
EOF
chmod 0644 /etc/grafana/provisioning/datasources/yanus-tracing.yml
python3 - <<'PY'
from pathlib import Path
import yaml
p=Path('/etc/grafana/provisioning/datasources/yanus.yml')
config=yaml.safe_load(p.read_text())
loki=[s for s in config['datasources'] if s['uid']=='yanus-loki']
if len(loki)!=1: raise SystemExit('Expected existing Loki datasource')
data=loki[0].setdefault('jsonData',{})
fields=[f for f in data.get('derivedFields',[]) if f.get('name')!='TraceID']
fields.append({'name':'TraceID','matcherRegex':'"traceId"\\s*:\\s*"([0-9a-f]{32})"','datasourceUid':'yanus-tempo','url':'$${__value.raw}','urlDisplayLabel':'요청 처리 구간 보기'})
data['derivedFields']=fields
p.write_text(yaml.safe_dump(config,sort_keys=False,allow_unicode=True))
PY
systemctl daemon-reload
systemctl enable --now tempo
for ((attempt=0; attempt<60; attempt++)); do if curl --max-time 2 -fsS http://127.0.0.1:3200/ready >/dev/null 2>&1; then break; fi; sleep 1; done
curl --max-time 2 -fsS http://127.0.0.1:3200/ready >/dev/null
systemctl reload nginx
systemctl restart grafana-server
for ((attempt=0; attempt<90; attempt++)); do
  if curl --max-time 2 -fsS "http://$monitor:3000/api/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl --max-time 2 -fsS "http://$monitor:3000/api/health" >/dev/null
trap - ERR INT TERM
printf 'Tempo installed; prior monitoring configuration: %s\n' "$backup"
