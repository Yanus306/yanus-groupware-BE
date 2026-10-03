import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'grafana', 'dashboards');
const datasource = { type: 'prometheus', uid: 'yanus-prometheus' };
const destinations = [
  ['yanus-overview', '01 전체 상태', '01-overview.json'],
  ['yanus-api', '02 API 요청·오류', '02-api.json'],
  ['yanus-logs', '03 로그', '03-logs.json'],
  ['yanus-hosts', '04 서버 자원', '04-hosts.json'],
  ['yanus-postgres', '05 PostgreSQL', '05-postgres.json'],
];
const environment = 'environment="$environment"';
const host = `${environment},instance=~"$instance"`;
const http = `${host},uri!~"/actuator.*|/v3/api-docs.*|/swagger-ui.*"`;
const interval = '$__rate_interval';
const requests = `sum(rate(http_server_requests_seconds_count{${http}}[${interval}]))`;
const errors = status => `(sum(rate(http_server_requests_seconds_count{${http},status=~"${status}"}[${interval}])) or (0 * ${requests})) / clamp_min(${requests},0.000001) * 100`;
const percentile = p => `histogram_quantile(${p},sum by(le)(rate(http_server_requests_seconds_bucket{${http}}[${interval}])))`;
const cpu = `100 * (1 - avg by(instance)(rate(node_cpu_seconds_total{${host},mode="idle"}[${interval}])))`;
const memory = `100 * (1 - node_memory_MemAvailable_bytes{${host}} / node_memory_MemTotal_bytes{${host}})`;
const disk = `100 * (1 - node_filesystem_avail_bytes{${host},mountpoint="/",fstype!~"tmpfs|overlay"} / node_filesystem_size_bytes{${host},mountpoint="/",fstype!~"tmpfs|overlay"})`;
const heap = `100 * sum(jvm_memory_used_bytes{${host},area="heap"}) / sum(jvm_memory_max_bytes{${host},area="heap",id=~".*Old.*|.*Tenured.*"})`;

function dashboard(uid, title) {
  return {
    id: null, uid, title, schemaVersion: 39, version: 1,
    tags: ['yanus', 'prod'], editable: false, graphTooltip: 1,
    timezone: 'Asia/Seoul', refresh: '30s', time: { from: 'now-30m', to: 'now' },
    timepicker: { refresh_intervals: ['15s', '30s', '1m', '5m'] },
    annotations: { list: [{ name: '배포 · 운영 기록', type: 'dashboard', datasource: { type: 'grafana', uid: '-- Grafana --' }, enable: true, hide: false, iconColor: '#5794F2', builtIn: 1 }] },
    links: destinations.map(([target, name]) => ({ title: name, type: 'link', url: `/d/${target}`, includeVars: true, keepTime: true, targetBlank: false })),
    templating: { list: [
      { name: 'environment', label: '환경', type: 'custom', query: 'prod', current: { text: 'prod', value: 'prod' }, options: [{ text: 'prod', value: 'prod', selected: true }], hide: 0 },
      { name: 'instance', label: '호스트', type: 'query', datasource, query: { query: 'label_values(up{environment="$environment",job="yanus-node"}, instance)', refId: 'variable' }, definition: 'label_values(up{environment="$environment",job="yanus-node"}, instance)', refresh: 1, multi: true, includeAll: true, allValue: '.*', current: { text: 'All', value: '$__all' }, options: [], sort: 1, hide: 0 },
    ] }, panels: [],
  };
}

function builder(board) {
  let y = 0;
  let id = 1;
  function panel(title, expressions, unit, x, w, h, type, extra = {}) {
    const specs = typeof expressions === 'string' ? [{ expr: expressions, legend: '{{instance}}' }] : expressions;
    const result = {
      id: id++, title, type, datasource, gridPos: { x, y, w, h },
      targets: specs.map((spec, index) => ({ refId: String.fromCharCode(65 + index), expr: spec.expr, legendFormat: spec.legend, range: true, editorMode: 'code' })),
      fieldConfig: { defaults: { unit, min: 0, decimals: 2, color: { mode: type === 'stat' ? 'thresholds' : 'palette-classic' }, noValue: '수집 없음', thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 85 }, { color: 'red', value: 95 }] }, custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 10, spanNulls: false, axisPlacement: 'auto' } }, overrides: [] },
      options: type === 'stat'
        ? { reduceOptions: { calcs: ['lastNotNull'], fields: '', values: false }, textMode: 'value_and_name', colorMode: 'value', graphMode: 'area', justifyMode: 'auto', orientation: 'auto' }
        : { tooltip: { mode: 'multi', sort: 'desc' }, legend: { displayMode: 'table', placement: 'bottom', calcs: ['lastNotNull', 'max'] } },
      ...extra,
    };
    board.panels.push(result);
    return result;
  }
  return {
    row(title) { board.panels.push({ id: id++, type: 'row', title, collapsed: false, panels: [], gridPos: { x: 0, y, w: 24, h: 1 } }); y += 1; },
    note(title, content, h = 3) { board.panels.push({ id: id++, type: 'text', title, gridPos: { x: 0, y, w: 24, h }, options: { mode: 'markdown', content } }); y += h; },
    stats(specs) {
      const w = 24 / specs.length;
      specs.forEach((s, i) => {
        const p = panel(s[0], s[1], s[2] || 'short', i * w, w, 4, 'stat');
        p.targets.forEach(t => { t.instant = true; t.range = false; });
        p.options.graphMode = 'none';
        p.options.textMode = 'value';
        p.fieldConfig.defaults.thresholds.steps = [{ color: 'green', value: null }];
        if (s[2] === 'percent') {
          p.fieldConfig.defaults.thresholds.steps = s[0].includes('비율')
            ? [{ color: 'green', value: null }, { color: 'orange', value: 1 }, { color: 'red', value: 5 }]
            : [{ color: 'green', value: null }, { color: 'orange', value: 85 }, { color: 'red', value: 95 }];
        }
        if (s[2] === 's') {
          p.fieldConfig.defaults.thresholds.steps = [{ color: 'green', value: null }, { color: 'orange', value: 0.5 }, { color: 'red', value: 1 }];
          p.fieldConfig.defaults.mappings = [{ type: 'special', options: { match: 'nan', result: { text: '요청 없음', color: 'text' } } }];
          p.fieldConfig.defaults.noValue = '요청 없음';
        }
        if (s[0] === 'Hikari Pending') p.fieldConfig.defaults.thresholds.steps = [{ color: 'green', value: null }, { color: 'red', value: 1 }];
        if (s[3] === 'up') {
          p.fieldConfig.defaults.thresholds.steps = [{ color: 'red', value: null }, { color: 'green', value: 1 }];
          p.fieldConfig.defaults.mappings = [{ type: 'value', options: { '0': { text: 'DOWN', color: 'red' }, '1': { text: 'UP', color: 'green' } } }];
        }
      }); y += 4;
    },
    graphs(specs) {
      const w = 24 / specs.length;
      specs.forEach((s, i) => panel(s[0], s[1], s[2] || 'short', i * w, w, 8, 'timeseries'));
      y += 8;
    },
    table(title, expression, unit = 'short') {
      const p = panel(title, expression, unit, 0, 24, 7, 'table', { options: { showHeader: true, cellHeight: 'sm', sortBy: [] }, transformations: [{ id: 'labelsToFields', options: { mode: 'columns' } }, { id: 'organize', options: { excludeByName: { Time: true }, renameByName: { Value: '현재 값', instance: '호스트', job: '수집 대상' } } }] });
      p.targets.forEach(t => { t.instant = true; t.range = false; t.format = 'table'; }); y += 7;
    },
  };
}

const boards = destinations.map(([uid, title]) => dashboard(uid, title));
let b = builder(boards[0]);
b.note('PROD 운영 흐름', '**상태 확인 → API·JVM·연결 풀 → 서버 자원 → PostgreSQL** 순서로 확인합니다. 상단 메뉴는 선택한 호스트와 시간을 유지합니다.\n\n`UP`은 수집 연결 상태입니다. 요청이 없는 구간의 지연 백분위는 계산되지 않습니다. 팀 알림 수신지는 아직 연결되지 않았습니다.');
b.row('서비스 · 요청');
b.stats([['백엔드 수집', `up{${host},job="yanus-backend"}`, 'short', 'up'], ['DB 연결', `pg_up{${host}}`, 'short', 'up'], ['RPS', requests, 'reqps'], ['4xx 비율', errors('4..'), 'percent'], ['5xx 비율', errors('5..'), 'percent'], ['P95', percentile(0.95), 's']]);
b.graphs([['요청량 · 상태 코드', [{ expr: `sum by(status)(rate(http_server_requests_seconds_count{${http}}[${interval}]))`, legend: '{{status}}' }], 'reqps'], ['응답 지연 P50 · P95 · P99', [0.5, 0.95, 0.99].map(p => ({ expr: percentile(p), legend: `P${p * 100}` })), 's']]);
b.row('애플리케이션 · 연결 풀');
b.stats([['JVM Heap', heap, 'percent'], ['Tomcat Busy', `tomcat_threads_busy_threads{${host},name=~".*8080.*"}`], ['Hikari Active', `hikaricp_connections_active{${host}}`], ['Hikari Pending', `hikaricp_connections_pending{${host}}`]]);
b.graphs([['CPU', cpu, 'percent'], ['메모리', memory, 'percent'], ['디스크 /', disk, 'percent']]);
b.row('수집 · 알림 조건');
b.table('수집 대상 상태', `up{${host},job=~"yanus-.*"}`);
b.note('알림 조건 확인', '아래 목록이 비어 있으면 현재 Pending·Firing 조건이 없습니다. 수집 중단 여부는 위의 수집 대상 상태에서 따로 확인합니다.', 2);
b.table('현재 Pending · Firing 조건', `ALERTS{environment="$environment",alertstate=~"pending|firing"}`);

b = builder(boards[1]);
b.note('API 요청·오류 분석', 'HTTP histogram으로 지연을 계산합니다. 업무 요청에서 Actuator·Swagger 조회는 제외합니다. `UNKNOWN` URI는 Spring이 경로를 확정하기 전 거부한 요청일 수 있습니다. 하단에서 JVM → Tomcat → Hikari 병목을 확인합니다.');
b.stats([['RPS', requests, 'reqps'], ['4xx 비율', errors('4..'), 'percent'], ['5xx 비율', errors('5..'), 'percent'], ['P50', percentile(0.5), 's'], ['P95', percentile(0.95), 's'], ['P99', percentile(0.99), 's']]);
b.row('HTTP · 엔드포인트');
b.graphs([['API별 요청량', [{ expr: `sum by(method,uri)(rate(http_server_requests_seconds_count{${http}}[${interval}]))`, legend: '{{method}} {{uri}}' }], 'reqps'], ['상태 코드별 요청량', [{ expr: `sum by(status)(rate(http_server_requests_seconds_count{${http}}[${interval}]))`, legend: '{{status}}' }], 'reqps']]);
b.graphs([['API별 P95', [{ expr: `histogram_quantile(0.95,sum by(method,uri,le)(rate(http_server_requests_seconds_bucket{${http}}[${interval}])))`, legend: '{{method}} {{uri}}' }], 's'], ['API별 P99', [{ expr: `histogram_quantile(0.99,sum by(method,uri,le)(rate(http_server_requests_seconds_bucket{${http}}[${interval}])))`, legend: '{{method}} {{uri}}' }], 's']]);
b.row('JVM · Heap · GC · Thread');
b.graphs([['Heap Used · Max', [{ expr: `sum(jvm_memory_used_bytes{${host},area="heap"})`, legend: 'Used' }, { expr: `sum(jvm_memory_max_bytes{${host},area="heap",id=~".*Old.*|.*Tenured.*"})`, legend: 'Max' }], 'bytes'], ['Non-Heap', [{ expr: `sum by(id)(jvm_memory_used_bytes{${host},area="nonheap"})`, legend: '{{id}}' }], 'bytes']]);
b.graphs([['GC Pause · 최대', [{ expr: `jvm_gc_pause_seconds_max{${host}}`, legend: '{{action}} {{cause}}' }], 's'], ['GC 횟수 / 초', [{ expr: `sum by(action)(rate(jvm_gc_pause_seconds_count{${host}}[${interval}]))`, legend: '{{action}}' }], 'ops']]);
b.graphs([['Live · Peak Thread', [{ expr: `jvm_threads_live_threads{${host}}`, legend: 'Live' }, { expr: `jvm_threads_peak_threads{${host}}`, legend: 'Peak' }]], ['Loaded Classes', `jvm_classes_loaded_classes{${host}}`]]);
b.row('Tomcat · 요청 처리');
b.graphs([['Busy · Current · Max Threads', ['busy', 'current', 'config_max'].map(n => ({ expr: `tomcat_threads_${n}_threads{${host},name=~".*8080.*"}`, legend: n }))], ['Connections · KeepAlive', [{ expr: `tomcat_connections_current_connections{${host},name=~".*8080.*"}`, legend: 'Connections' }, { expr: `tomcat_connections_keepalive_current_connections{${host},name=~".*8080.*"}`, legend: 'KeepAlive' }]]]);
b.row('Hikari · DB 연결');
b.graphs([['Active · Idle · Max', ['active', 'idle', 'max'].map(n => ({ expr: `hikaricp_connections_${n}{${host}}`, legend: `{{pool}} ${n}` }))], ['Pending · Timeout', [{ expr: `hikaricp_connections_pending{${host}}`, legend: 'Pending' }, { expr: `rate(hikaricp_connections_timeout_total{${host}}[${interval}])`, legend: 'Timeout / sec' }]]]);
b.graphs([['Connection Acquire 평균', `rate(hikaricp_connections_acquire_seconds_sum{${host}}[${interval}]) / clamp_min(rate(hikaricp_connections_acquire_seconds_count{${host}}[${interval}]),0.000001)`, 's'], ['Connection Usage 평균', `rate(hikaricp_connections_usage_seconds_sum{${host}}[${interval}]) / clamp_min(rate(hikaricp_connections_usage_seconds_count{${host}}[${interval}]),0.000001)`, 's']]);

b = builder(boards[2]);
b.note('로그 보존 · 확인', 'PROD 애플리케이션 로그는 파일에 보존합니다. 이 화면은 **로그 레벨별 발생량과 보존 상태**를 보여줍니다. 로그 본문 검색은 아래 SSH 명령으로 확인합니다.');
const logs = `${environment},instance="app-server"`;
b.stats([['파일 존재', `yanus_log_file_exists{${logs}}`, 'short', 'up'], ['현재 파일 크기', `yanus_log_file_size_bytes{${logs}}`, 'bytes'], ['보존 일수', `yanus_log_retention_days{${logs}}`, 'd'], ['압축 파일', `yanus_log_archives{${logs}}`]]);
b.graphs([['레벨별 로그 / 초', [{ expr: `sum by(level)(rate(logback_events_total{${environment}}[${interval}]))`, legend: '{{level}}' }], 'ops'], ['WARN · ERROR 발생량', [{ expr: `sum by(level)(increase(logback_events_total{${environment},level=~"warn|error"}[$__range]))`, legend: '{{level}}' }]]]);
b.graphs([['로그 파일 크기', `yanus_log_file_size_bytes{${logs}}`, 'bytes'], ['보존 상태 수집 경과', `time() - yanus_log_collection_timestamp_seconds{${logs}}`, 's']]);
b.note('애플리케이션 로그 조회', '```bash\nssh app-server\nsudo tail -n 200 /var/log/yanus/prod/application.log\nsudo journalctl -u yanus --since "30 minutes ago" --no-pager\nsudo grep -E "WARN|ERROR" /var/log/yanus/prod/application.log | tail -n 100\n```\n\n파일은 날짜·20MB 단위로 gzip 회전하며 14일 / 총 300MB를 상한으로 보존합니다. Logback이 회전하므로 같은 파일에 logrotate를 중복 적용하지 않습니다.', 7);
b.note('HTTP · Nginx 로그', '```bash\nsudo tail -n 100 /var/log/nginx/access.log\nsudo tail -n 100 /var/log/nginx/error.log\n```\n\n조회 시간은 대시보드와 맞추세요. 로그 본문과 토큰·개인정보는 이슈나 공유 캡처에 그대로 옮기지 않습니다.', 6);

b = builder(boards[3]);
b.note('운영 호스트 자원', '호스트 선택으로 앱 서버와 DB 서버를 구분합니다. CPU·메모리·디스크·네트워크의 변화와 API 지연 시간을 함께 확인하세요.');
b.stats([['Node 수집', `up{${host},job="yanus-node"}`, 'short', 'up'], ['CPU', cpu, 'percent'], ['메모리', memory, 'percent'], ['디스크 /', disk, 'percent']]);
b.row('CPU · Load');
b.graphs([['CPU 모드', [{ expr: `100 * avg by(instance,mode)(rate(node_cpu_seconds_total{${host},mode=~"user|system|iowait|idle"}[${interval}]))`, legend: '{{instance}} {{mode}}' }], 'percent'], ['Load 1m · 5m · 15m', [1, 5, 15].map(n => ({ expr: `node_load${n}{${host}}`, legend: `{{instance}} ${n}m` }))]]);
b.row('메모리 · Swap');
b.graphs([['Available · Cache', ['MemAvailable', 'Cached'].map(n => ({ expr: `node_memory_${n}_bytes{${host}}`, legend: `{{instance}} ${n}` })), 'bytes'], ['Swap 사용', `node_memory_SwapTotal_bytes{${host}} - node_memory_SwapFree_bytes{${host}}`, 'bytes']]);
b.row('디스크');
b.graphs([['파일시스템 사용률 /', disk, 'percent'], ['가용 용량 /', `node_filesystem_avail_bytes{${host},mountpoint="/",fstype!~"tmpfs|overlay"}`, 'bytes']]);
const devices = `${host},device!~"loop.*|ram.*|sr.*|dm-.*"`;
b.graphs([['Read · Write 처리량', ['read', 'written'].map(n => ({ expr: `rate(node_disk_${n}_bytes_total{${devices}}[${interval}])`, legend: `{{instance}} {{device}} ${n}` })), 'Bps'], ['Read · Write 평균 지연', [{ expr: `rate(node_disk_read_time_seconds_total{${devices}}[${interval}]) / clamp_min(rate(node_disk_reads_completed_total{${devices}}[${interval}]),0.000001)`, legend: '{{instance}} {{device}} read' }, { expr: `rate(node_disk_write_time_seconds_total{${devices}}[${interval}]) / clamp_min(rate(node_disk_writes_completed_total{${devices}}[${interval}]),0.000001)`, legend: '{{instance}} {{device}} write' }], 's']]);
b.row('네트워크');
const network = `${host},device!~"lo|veth.*|docker.*|br-.*"`;
b.graphs([['RX · TX Bytes', ['receive', 'transmit'].map(n => ({ expr: `rate(node_network_${n}_bytes_total{${network}}[${interval}])`, legend: `{{instance}} {{device}} ${n}` })), 'Bps'], ['RX · TX Packets', ['receive', 'transmit'].map(n => ({ expr: `rate(node_network_${n}_packets_total{${network}}[${interval}])`, legend: `{{instance}} {{device}} ${n}` })), 'pps']]);
b.graphs([['Network Errors', ['receive', 'transmit'].map(n => ({ expr: `rate(node_network_${n}_errs_total{${network}}[${interval}])`, legend: `{{instance}} ${n}` })), 'ops'], ['Dropped Packets', ['receive', 'transmit'].map(n => ({ expr: `rate(node_network_${n}_drop_total{${network}}[${interval}])`, legend: `{{instance}} ${n}` })), 'pps']]);

b = builder(boards[4]);
b.note('PostgreSQL · 운영 DB', '현재 DB는 자체 운영 PostgreSQL입니다. DB 서버 자원과 PostgreSQL 내부 통계를 함께 확인합니다. Hikari 연결 풀 지표는 02 API 요청·오류에서 확인하세요.');
const pg = environment;
b.stats([['DB 연결', `pg_up{${pg}}`, 'short', 'up'], ['연결 수', `sum(pg_stat_database_numbackends{${pg},datname!~"template.*"})`], ['DB 크기', `pg_database_size_bytes{${pg},datname="yanus"}`, 'bytes'], ['DB 서버 CPU', `100 * (1 - avg(rate(node_cpu_seconds_total{${environment},instance="data-server",mode="idle"}[${interval}])))`, 'percent']]);
b.row('DB 연결 · 처리');
b.graphs([['DB별 연결 수', [{ expr: `pg_stat_database_numbackends{${pg},datname!~"template.*"}`, legend: '{{datname}}' }]], ['Transaction Commit · Rollback', ['commit', 'rollback'].map(n => ({ expr: `rate(pg_stat_database_xact_${n}{${pg},datname="yanus"}[${interval}])`, legend: n })), 'ops']]);
b.graphs([['Cache Hit Ratio', `100 * rate(pg_stat_database_blks_hit{${pg},datname="yanus"}[${interval}]) / clamp_min(rate(pg_stat_database_blks_hit{${pg},datname="yanus"}[${interval}]) + rate(pg_stat_database_blks_read{${pg},datname="yanus"}[${interval}]),0.000001)`, 'percent'], ['Deadlocks · Conflicts', [{ expr: `rate(pg_stat_database_deadlocks{${pg},datname="yanus"}[${interval}])`, legend: 'Deadlocks' }, { expr: `rate(pg_stat_database_conflicts{${pg},datname="yanus"}[${interval}])`, legend: 'Conflicts' }], 'ops']]);
b.graphs([['Tuple 처리량', ['returned', 'fetched', 'inserted', 'updated', 'deleted'].map(n => ({ expr: `rate(pg_stat_database_tup_${n}{${pg},datname="yanus"}[${interval}])`, legend: n })), 'ops'], ['Temp Bytes', `rate(pg_stat_database_temp_bytes{${pg},datname="yanus"}[${interval}])`, 'Bps']]);
b.row('DB 서버 · 자원');
const dbHost = `${environment},instance="data-server"`;
b.graphs([['DB 서버 가용 메모리', `node_memory_MemAvailable_bytes{${dbHost}}`, 'bytes'], ['DB 서버 디스크 /', `100 * (1 - node_filesystem_avail_bytes{${dbHost},mountpoint="/"} / node_filesystem_size_bytes{${dbHost},mountpoint="/"})`, 'percent']]);
b.note('DB 지연 해석', '기본 PostgreSQL 통계만으로 SQL별 지연이나 RDS CloudWatch 지표를 만들지 않습니다. 지연이 늘면 API P95 → Hikari Pending → DB 연결·트랜잭션 → 서버 I/O 순서로 확인합니다. `pg_stat_statements`와 의도적인 부하 실험은 별도 작업입니다.', 3);

fs.mkdirSync(directory, { recursive: true });
for (const [index, board] of boards.entries()) {
  fs.writeFileSync(path.join(directory, destinations[index][2]), `${JSON.stringify(board, null, 2)}\n`);
}
console.log(JSON.stringify(boards.map(board => ({ uid: board.uid, title: board.title, panels: board.panels.length })), null, 2));
