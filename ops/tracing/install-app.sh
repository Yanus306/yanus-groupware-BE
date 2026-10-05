#!/usr/bin/env bash
set -Eeuo pipefail
umask 027
[[ $(id -u) == 0 && $# == 3 ]] || { echo 'root MONITOR_IP TRACING_JAR PASSWORD_FILE required' >&2; exit 1; }
monitor=$1
bundle=$2
password=$3
[[ $monitor =~ ^192\.168\.0\.[0-9]{1,3}$ && -f $bundle && -f $password && ! -L $bundle && ! -L $password ]] || exit 1
exec 9>/var/lock/yanus-deploy.lock
flock -n 9 || { echo 'DEPLOYMENT_LOCKED' >&2; exit 75; }
source_directory=$(cd -- "$(dirname -- "$0")" && pwd)
backup=/var/backups/yanus-tracing/app-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
paths=(/etc/alloy/config.alloy /etc/alloy/tempo-authorization /etc/yanus/otel.properties /etc/systemd/system/yanus.service.d/zz-tracing.conf /opt/yanus-tracing)
for path in "${paths[@]}"; do [[ ! -e $path ]] || cp --parents -a "$path" "$backup"; done
sha256sum /home/yanus/app/app.jar > "$backup/app.sha256"
curl --max-time 5 -fsS http://127.0.0.1:8080/v3/api-docs > "$backup/openapi-before.json"
rollback() {
  trap - ERR INT TERM
  for path in "${paths[@]}"; do
    rm -rf -- "$path"
    [[ ! -e $backup$path ]] || cp -a "$backup$path" "$path"
  done
  systemctl daemon-reload
  systemctl restart alloy || true
  systemctl restart yanus || true
  for ((attempt=0; attempt<60; attempt++)); do if curl --max-time 2 -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null 2>&1; then break; fi; sleep 1; done
  curl --max-time 2 -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null || echo 'ROLLBACK_READINESS_FAILED' >&2
  echo "TRACING_INSTALL_FAILED: restored $backup" >&2
  exit 1
}
trap rollback ERR INT TERM
install -d -m 0755 /opt/yanus-tracing/lib
curl --connect-timeout 10 --max-time 120 -fsSL https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.32.0/opentelemetry-javaagent.jar -o /opt/yanus-tracing/opentelemetry-javaagent.jar
printf 'f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82  /opt/yanus-tracing/opentelemetry-javaagent.jar\n' | sha256sum -c -
chmod 0644 /opt/yanus-tracing/opentelemetry-javaagent.jar
install -m 0644 "$bundle" /opt/yanus-tracing/lib/yanus-tracing.jar
install -m 0644 "$source_directory/otel.properties" /etc/yanus/otel.properties
python3 - "$password" <<'PY'
from pathlib import Path
import base64,sys,os,grp
p=Path('/etc/alloy/tempo-authorization')
p.write_text('Basic '+base64.b64encode(b'alloy:'+Path(sys.argv[1]).read_bytes().strip()).decode())
os.chmod(p,0o640);os.chown(p,0,grp.getgrnam('alloy').gr_gid)
PY
python3 - "$source_directory/alloy-tracing.config" "$monitor" <<'PY'
from pathlib import Path
import sys
p=Path('/etc/alloy/config.alloy'); text=p.read_text()
if 'otelcol.receiver.otlp "yanus_traces"' in text: text=text.split('// BEGIN YANUS TRACING',1)[0]
snippet=Path(sys.argv[1]).read_text().replace('MONITORING_ADDRESS',sys.argv[2])
p.write_text(text.rstrip()+'\n\n// BEGIN YANUS TRACING\n'+snippet)
PY
alloy validate /etc/alloy/config.alloy
systemctl restart alloy
install -m 0644 "$source_directory/yanus-tracing.conf" /etc/systemd/system/yanus.service.d/zz-tracing.conf
systemctl daemon-reload
systemctl restart yanus
# 운영 2GB VM에서 Java agent를 포함한 첫 부팅은 94.79초가 걸렸다.
for ((attempt=0; attempt<180; attempt++)); do if curl --max-time 2 -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null 2>&1; then break; fi; sleep 1; done
curl --max-time 2 -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null
curl --max-time 5 -fsS http://127.0.0.1:8080/v3/api-docs > "$backup/openapi-after.json"
cmp "$backup/openapi-before.json" "$backup/openapi-after.json"
sha256sum -c "$backup/app.sha256"
trap - ERR INT TERM
printf 'Tracing installed without replacing application JAR; rollback: %s\n' "$backup"
