#!/usr/bin/env bash
set -Eeuo pipefail
[[ $(id -u) == 0 && $# == 1 && -f $1 ]] || exit 1
backup=/var/backups/yanus-tracing/dashboards-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$backup"
cp -a /var/lib/grafana/yanus-dashboards /etc/prometheus/prometheus.yml "$backup/"
rollback() {
  trap - ERR
  cp -a "$backup/prometheus.yml" /etc/prometheus/prometheus.yml
  cp -a "$backup/yanus-dashboards/." /var/lib/grafana/yanus-dashboards/
  [[ -f $backup/yanus-dashboards/06-traces.json ]] || rm -f /var/lib/grafana/yanus-dashboards/06-traces.json
  systemctl reload prometheus || true
  exit 1
}
trap rollback ERR
install -m 0644 -o grafana -g grafana "$1" /var/lib/grafana/yanus-dashboards/06-traces.json
python3 - <<'PY'
from pathlib import Path
import json,yaml
directory=Path('/var/lib/grafana/yanus-dashboards')
for name in ['01-overview.json','02-api.json','03-logs.json','04-hosts.json','05-postgres.json']:
 p=directory/name; board=json.loads(p.read_text())
 links=[l for l in board.get('links',[]) if l.get('url')!='/d/yanus-traces']
 links.append({'title':'06 요청 추적','type':'link','url':'/d/yanus-traces','includeVars':True,'keepTime':True,'targetBlank':False})
 board['links']=links
 if board['uid']=='yanus-logs':
  for panel in board['panels']:
   if panel.get('type')=='logs':
    description=panel.get('description','')
    description=description.split(' 로그 상세 TraceID의 요청 처리 구간 보기로 Tempo를 엽니다.',1)[0]
    panel['description'] = description + ' 로그 상세 TraceID의 요청 처리 구간 보기로 Tempo를 엽니다. traceFlags의 최하위 비트가 0인 00/02 등은 샘플링되지 않아 추적이 없을 수 있습니다.'
 p.write_text(json.dumps(board,ensure_ascii=False,indent=2)+'\n')
p=Path('/etc/prometheus/prometheus.yml'); config=yaml.safe_load(p.read_text())
jobs=[j for j in config['scrape_configs'] if j.get('job_name')!='yanus-tempo']
jobs.append({'job_name':'yanus-tempo','static_configs':[{'targets':['127.0.0.1:3200'],'labels':{'environment':'prod','service':'tracing','instance':'monitoring-server'}}]})
config['scrape_configs']=jobs;p.write_text(yaml.safe_dump(config,sort_keys=False))
PY
promtool check config /etc/prometheus/prometheus.yml
systemctl reload prometheus
trap - ERR
printf 'Tracing dashboard installed; previous dashboards: %s\n' "$backup"
