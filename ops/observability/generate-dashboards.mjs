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

const help = {
  'yanus-overview': `### 서비스가 안 되거나 느릴 때 여기서 시작하세요
| 증상 | 먼저 볼 지표 | 다음 점검 |
| --- | --- | --- |
| 화면·API가 응답하지 않음 | 백엔드 수집, DB 연결, 수집 대상 상태 | DOWN 대상의 service/exporter 상태와 연결을 확인합니다. UP이어도 업무 오류는 02에서 확인합니다. |
| 서버 오류가 늘어남 | 5xx 비율, 요청량·상태 코드 | 02에서 문제가 생긴 URI·시간을 좁힌 뒤 03의 ERROR 로그를 같은 시간으로 조회합니다. |
| 로그인·권한 요청 실패 | 4xx 비율 | 401·403인지 400·404인지 02에서 구분하고 요청·인증·권한을 확인합니다. 4xx만 보고 서버를 재시작하지 않습니다. |
| 요청이 평소보다 느림 | P95, Hikari Pending, CPU·메모리 | 02의 JVM·Tomcat·Hikari → 05 PostgreSQL → 04 서버 I/O 순서로 좁힙니다. |
| 숫자나 그래프가 비어 있음 | 시간·호스트 선택, 수집 UP | All·최근 30분으로 되돌립니다. 요청 없음은 무요청 구간이며, DOWN이나 수집 없음과 구분합니다. |

**운영 순서:** 장애 시각과 영향 범위를 기록 → 아래 화면에서 근거 확인 → 원인을 확인한 뒤 조치 → 같은 지표와 실제 API로 회복 확인. 알림 조건 목록이 비어 있으면 현재 Pending·Firing이 없는 것입니다.`,
  'yanus-api': `### API 오류·지연을 원인별로 구분하세요
| 상황 | 확인 순서 | 조치 판단 |
| --- | --- | --- |
| 5xx 증가 | 상태 코드 → API별 요청량·P95 → ERROR 로그 | 특정 URI만 영향받는지 확인하고 예외·DB·외부 서비스 오류를 구분합니다. |
| P95·P99 증가 | 요청량 변화 → GC Pause → Tomcat Busy → Hikari Pending | 요청 수와 함께 해석합니다. 적은 요청의 백분위로 평상시 성능을 단정하지 않습니다. |
| Heap이 계속 증가 | Heap Used·Max + GC Pause·횟수 | GC 후에도 내려오지 않는지 확인합니다. 톱니 모양의 증가·회수는 정상일 수 있습니다. |
| Tomcat Busy가 Max에 접근 | Busy·Current·Max + API 지연 | 긴 요청·DB 대기를 먼저 확인합니다. 스레드 수부터 늘리면 DB 병목이 커질 수 있습니다. |
| Hikari Pending 또는 Timeout 발생 | Active가 Max에 붙는지 → Acquire 지연 → 05 DB 연결·처리 | 느린 SQL·트랜잭션·DB 연결 문제를 확인한 뒤 풀 크기 변경을 판단합니다. |
| 4xx만 증가 | 400·401·403·404 구분 + URI | 요청 값, 인증 만료, 권한, 경로를 확인합니다. UNKNOWN은 Spring 경로 매핑 전 거부일 수 있습니다. |

**P95:** 요청 100개 중 대략 95개가 이 시간 이내에 완료됐다는 뜻입니다. 상단 값은 현재 계산 구간, 하단 그래프는 선택 기간의 추세입니다. 무요청 구간은 지연을 계산하지 않습니다.`,
  'yanus-logs': `### 오류 원문과 보존 문제를 구분하세요
| 상황 | 무엇을 볼지 | 확인 방법 |
| --- | --- | --- |
| 5xx·지연 원인 확인 | ERROR·WARN 발생량과 API 오류 시각 | 02에서 시간을 좁히고 아래 로그 본문을 확인합니다. 응답의 X-Request-ID를 요청 ID 입력칸에 넣으면 관련 요청·예외를 모아 볼 수 있습니다. |
| WARN 증가, 서비스는 정상 | 레벨별 발생 추세 | 반복 경고의 원인을 확인합니다. WARN 수만으로 장애를 확정하지 않습니다. |
| 파일 존재 DOWN | 파일 존재·로그 파일 경로 | yanus 서비스와 디렉터리 권한을 확인하고 journalctl에서 로그 초기화 실패를 찾습니다. |
| 보존 상태 수집 경과가 계속 증가 | metadata 수집 경과·Node 수집 | yanus-log-metrics.timer와 service 실행 결과, textfile 수집을 확인합니다. 수집은 1분 간격입니다. |
| 로그 크기·디스크 증가 | 현재 파일·압축 파일 수 + 04 디스크 | 회전·보존 정책과 반복 오류를 확인합니다. 원인·증거를 확보하기 전에 로그를 지우지 않습니다. |

**본문은 Loki, 발생량은 Micrometer입니다.** 요청 ID는 응답의 X-Request-ID 또는 로그 상세에서 확인합니다. 빈 요청 ID 입력칸은 전체 요청을 보여줍니다. 4xx는 인증·권한·업무 거절을 구분하고, 5xx는 exceptionType·stackTrace의 원인 유형과 발생 위치를 확인합니다. 예외 원문 메시지·body·토큰은 저장하지 않습니다. Loki 본문은 7일, 원본 파일은 14일·300MB 상한입니다.`,
  'yanus-hosts': `### 앱·DB·모니터링 서버를 먼저 선택하세요
| 상황 | 함께 볼 지표 | 다음 점검 |
| --- | --- | --- |
| CPU가 높고 API도 느림 | CPU user·system·iowait, Load, 02 GC | CPU 계산 작업인지 디스크 대기인지 구분합니다. Load는 CPU 수와 함께 판단합니다. |
| 메모리가 부족함 | Available, Cache, Swap, 02 JVM Heap | 가용 메모리 감소와 Swap 증가가 지속되는지 확인합니다. Linux Cache 사용 자체는 장애가 아닙니다. |
| Grafana 조회만 느림 | monitoring-server CPU·Available·디스크 I/O | 모니터링 서버를 선택하고 Grafana·Prometheus·Loki 상태를 확인합니다. 수집 15초와 화면 갱신 30초는 조회 지연과 구분합니다. |
| 저장·DB 요청이 느림 | Read·Write 지연·처리량, iowait | data-server라면 05의 DB 처리와 함께 확인합니다. 처리량 증가만으로 느린 디스크를 단정하지 않습니다. |
| 디스크가 가득 참 | 루트 사용률·가용 용량 + 03 파일 보존 | 85% 경고·95% 긴급 기준을 확인하고 증가한 파일을 조사합니다. DB·TSDB·로그를 임의 삭제하지 않습니다. |
| 외부 연결 실패·지연 | RX·TX, Errors, Dropped | 실제 인터페이스의 오류가 지속되는지 확인하고 방화벽·링크·목적지 연결을 점검합니다. |

**호스트 비교:** All로 동시에 확인한 뒤 문제가 있는 서버 하나를 선택합니다. 자원 상승 시각을 API P95·DB 처리와 맞춰 원인인지 결과인지 확인합니다.`,
  'yanus-postgres': `### DB 문제인지 연결 풀 문제인지 구분하세요
| 상황 | 확인 순서 | 다음 점검 |
| --- | --- | --- |
| DB 연결 DOWN | pg_up → postgres/exporter 상태 | DB 자체 중단과 exporter 인증·socket 문제를 구분합니다. 지표만 보고 DB를 재시작하지 않습니다. |
| Hikari Pending·Timeout 증가 | 02 Active·Max → DB별 연결 → DB CPU·I/O | 긴 트랜잭션·DB 연결 한도·느린 처리를 확인합니다. 풀만 늘리지 않습니다. |
| API가 느리고 DB 처리량 변화 | Commit·Rollback, Cache Hit, Tuple·Temp Bytes | 작업량 증가, 롤백 반복, 디스크 읽기·임시 파일 증가를 함께 확인합니다. |
| Deadlock 증가 | Deadlocks 추세 + 같은 시각의 예외 | 충돌한 트랜잭션과 잠금 순서를 조사합니다. PostgreSQL 로그와 요청 정보를 연결합니다. |
| DB 크기·디스크 증가 | DB 크기, Temp Bytes, data-server 디스크 | 데이터 증가와 일시적 작업을 구분하고 보존·용량 계획을 확인합니다. |

**해석 범위:** 자체 운영 PostgreSQL의 기본 통계입니다. Cache Hit는 DB 활동이 있는 구간에서 해석하며, 이 화면만으로 SQL별 지연이나 잠금 원인을 확정할 수 없습니다. 쿼리별 분석은 별도 DB 조사로 진행합니다.`,
};

function description(title) {
  const guidance = [
    [/UP|백엔드 수집|DB 연결|Node 수집|수집 대상 상태|파일 존재/, '연결·수집 성공 여부입니다. DOWN이면 서비스 상태, exporter와 네트워크를 구분해 확인하세요. UP은 업무 기능 전체의 정상 보장이 아닙니다.'],
    [/4xx/, '인증·권한·요청 값·경로 문제를 구분하세요. 상태 코드별 요청량과 URI를 확인하고 같은 시각의 로그를 조회합니다.'],
    [/5xx|상태 코드/, '서버 오류가 늘면 영향 URI와 요청량을 함께 보고 ERROR 로그·DB·외부 서비스 연결을 확인하세요.'],
    [/P50|P95|P99|응답 지연/, 'P95는 요청의 약 95%가 완료되는 시간입니다. 요청량·GC·Tomcat·Hikari와 함께 보고 무요청 구간은 계산하지 않습니다.'],
    [/RPS|요청량/, '초당 요청 수입니다. 평소 트래픽 및 오류·지연과 비교하세요. 0은 무요청일 수 있으며 수집 UP을 따로 확인합니다.'],
    [/Hikari|Active|Idle|Pending|Acquire|Connection Usage/, '연결 대기·Timeout이 생기면 Active가 Max에 도달하는지 확인하고 PostgreSQL 처리·긴 트랜잭션을 조사하세요. 풀 크기부터 늘리지 않습니다.'],
    [/Tomcat|Busy|KeepAlive/, '요청 처리 스레드·연결입니다. Busy가 Max에 접근하면서 지연이 늘면 긴 요청·DB 대기를 먼저 확인하세요.'],
    [/Heap|GC|Thread|Classes/, 'Heap은 GC 후 회수되는지, Pause는 지연 시각과 겹치는지 확인하세요. 톱니 모양의 Heap 추세는 정상일 수 있습니다.'],
    [/CPU|Load/, '지속 상승과 API·DB 지연을 함께 보세요. user/system과 iowait를 구분하고 Load는 CPU 개수와 비교합니다.'],
    [/메모리|Available|Swap/, '가용 메모리 감소와 Swap 증가가 지속되는지 확인하세요. Cache 사용량 자체는 메모리 장애가 아닙니다.'],
    [/디스크|용량|Read|Write|Temp Bytes|DB 크기/, '사용률·가용 용량·I/O 지연을 함께 확인하세요. 증가한 파일과 작업을 조사하고 DB·로그·TSDB를 임의 삭제하지 않습니다.'],
    [/로그|보존|파일 크기|압축 파일|WARN|ERROR/, 'API 오류 시각과 맞춰 파일·journal 원문을 확인하세요. 이 패널은 발생량 또는 보존 메타데이터이며 로그 본문 검색이 아닙니다.'],
    [/Network|RX|TX|Packets/, '실제 인터페이스의 오류·드롭이 지속되는지 보고 링크·방화벽·목적지 연결을 확인하세요.'],
    [/Cache Hit/, 'DB 활동이 있는 구간에서 해석하세요. 낮은 비율과 디스크 읽기·API 지연이 함께 증가하는지 확인합니다.'],
    [/Deadlocks|Transaction|Tuple|DB별 연결|연결 수/, 'DB 처리·연결 추세를 Hikari 대기 및 API 지연과 비교하세요. Deadlock은 같은 시각의 DB·애플리케이션 로그로 원인을 조사합니다.'],
    [/Firing/, '현재 Pending·Firing 조건만 표시합니다. 목록이 비어 있으면 해당 조건이 없습니다. 외부 알림 수신지는 아직 연결되지 않았습니다.'],
  ];
  return guidance.find(([pattern]) => pattern.test(title))?.[1]
    || '선택한 호스트·기간의 실제 메트릭입니다. 단일 값보다 추세와 관련 API·서버·DB 지표를 함께 확인하세요.';
}

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
      id: id++, title, description: description(title), type, datasource, gridPos: { x, y, w, h },
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
        p.options.textMode = ['Node 수집', 'CPU', '메모리', '디스크 /'].includes(s[0])
          ? 'value_and_name' : 'value';
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
    logs(title, expression) {
      const source = { type: 'loki', uid: 'yanus-loki' };
      board.panels.push({ id: id++, title, description: '응답 X-Request-ID 또는 로그 상세의 requestId를 입력하세요. 빈 입력은 전체 로그입니다. 상세에서 errorCode, exceptionType, stackTrace, status, elapsedMs를 확인합니다.', type: 'logs', datasource: source, gridPos: { x: 0, y, w: 24, h: 12 }, targets: [{ refId: 'A', datasource: source, expr: expression, queryType: 'range', maxLines: 200 }], options: { showTime: true, showLabels: false, showCommonLabels: false, wrapLogMessage: true, enableLogDetails: true, prettifyLogMessage: true, sortOrder: 'Descending', dedupStrategy: 'none' }, fieldConfig: { defaults: {}, overrides: [] } });
      y += 12;
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
b.note('로그 보존 · 확인', 'PROD 요청·예외 로그를 Loki에서 조회합니다. **요청 ID**에 응답의 X-Request-ID를 넣어 요청 완료·업무 오류·외부 서비스 이벤트를 연결하세요. 본문은 7일, 원본 파일은 14일·300MB 상한으로 보존합니다.');
boards[2].templating.list.push({ name: 'requestId', label: '요청 ID', type: 'textbox', query: '', current: { text: '', value: '' }, options: [], hide: 0 });
const logs = `${environment},instance="app-server"`;
b.stats([['파일 존재', `yanus_log_file_exists{${logs}}`, 'short', 'up'], ['현재 파일 크기', `yanus_log_file_size_bytes{${logs}}`, 'bytes'], ['보존 일수', `yanus_log_retention_days{${logs}}`, 'd'], ['압축 파일', `yanus_log_archives{${logs}}`]]);
b.graphs([['레벨별 로그 / 초', [{ expr: `sum by(level)(rate(logback_events_total{${environment}}[${interval}]))`, legend: '{{level}}' }], 'ops'], ['WARN · ERROR 발생량', [{ expr: `sum by(level)(increase(logback_events_total{${environment},level=~"warn|error"}[$__range]))`, legend: '{{level}}' }]]]);
b.graphs([['로그 파일 크기', `yanus_log_file_size_bytes{${logs}}`, 'bytes'], ['보존 상태 수집 경과', `time() - yanus_log_collection_timestamp_seconds{${logs}}`, 's']]);
b.row('요청 · 예외 본문');
b.logs('요청 ID로 로그 추적', '{environment="$environment",service="backend",instance="app-server"} |= ${requestId:doublequote} | json');
b.note('애플리케이션 로그 조회', '```bash\nssh app-server\nsudo tail -n 200 /var/log/yanus/prod/application.log\nsudo journalctl -u yanus --since "30 minutes ago" --no-pager\nsudo grep -E "WARN|ERROR" /var/log/yanus/prod/application.log | tail -n 100\n```\n\n파일은 날짜·20MB 단위로 gzip 회전하며 14일 / 총 300MB를 상한으로 보존합니다. Logback이 회전하므로 같은 파일에 logrotate를 중복 적용하지 않습니다.', 7);
b.note('HTTP · Nginx 로그', '```bash\nsudo tail -n 100 /var/log/nginx/access.log\nsudo tail -n 100 /var/log/nginx/error.log\n```\n\n조회 시간은 대시보드와 맞추세요. 로그 본문과 토큰·개인정보는 이슈나 공유 캡처에 그대로 옮기지 않습니다.', 6);

b = builder(boards[3]);
b.note('운영 호스트 자원', '호스트 선택으로 app-server·data-server·monitoring-server를 구분합니다. CPU·메모리·디스크·네트워크의 변화와 API 지연 시간을 함께 확인하세요.');
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
b.row('DB 백업 · Mac 사본');
const backupHost = `${environment},instance="data-server"`;
b.note('백업 도움말', '**최근 실행 실패:** data-server의 `journalctl -u yanus-db-backup.service`와 timer·디스크를 확인하세요. **Mac 사본 지연:** Mac 잠자기·오프라인·SSH와 collector 상태 파일을 확인합니다. 서버는 매일 03:20 KST, Mac은 로그인 시와 실행 중 매시간 수집합니다.\n\n아래 시각은 **복구 가능한 snapshot 시점**입니다. 과거 파일을 다시 수집해도 시점은 갱신되지 않습니다. Mac ACK는 당시 checksum 확인이며, 실제 복원 가능 여부는 별도 격리 복원 훈련으로 검증합니다. 암호화 파일은 14일 기준으로 보존하되 최신 사본과 Mac 미전송 서버 백업은 남깁니다. 복호화 키는 Mac에만 있어 키까지 잃으면 복원할 수 없습니다.', 5);
b.stats([['백업 최근 실행', `yanus_backup_last_run_success{${backupHost}}`, 'short', 'up'], ['서버 백업 시점', `(yanus_backup_local_snapshot_timestamp_seconds{${backupHost}} > 0) * 1000`, 'dateTimeAsIso'], ['Mac 사본 시점', `(yanus_backup_offsite_snapshot_timestamp_seconds{${backupHost}} > 0) * 1000`, 'dateTimeAsIso'], ['암호화 백업 크기', `yanus_backup_archive_bytes{${backupHost}}`, 'bytes']]);
for (const panel of boards[4].panels.slice(-4)) {
  panel.description = '서버 생성과 Mac 사본 확인을 구분합니다. 복구 시점은 snapshot 시각이며 ACK 수신 시각이 아닙니다. 실패·지연 시 위 도움말의 순서로 점검하세요.';
  if (panel.title === '백업 최근 실행') panel.fieldConfig.defaults.mappings = [{ type: 'value', options: { '0': { text: '실패', color: 'red' }, '1': { text: '성공', color: 'green' } } }];
  if (panel.title.endsWith('시점')) panel.fieldConfig.defaults.noValue = '유효 백업 시점 없음';
}
b.graphs([['서버 백업 경과', `time() - (yanus_backup_local_snapshot_timestamp_seconds{${backupHost}} > 0)`, 's'], ['Mac 복구 시점 경과', `time() - (yanus_backup_offsite_snapshot_timestamp_seconds{${backupHost}} > 0)`, 's']]);
for (const panel of boards[4].panels.slice(-2)) panel.description = '서버 26시간·Mac 30시간 초과는 지연 경고입니다. Mac 오프라인으로 사본 전달이 늦어질 수 있습니다.';

for (const uid of Object.keys(help)) {
  help[uid] += '\n\n**장애 알림·외부 점검**: [대응 절차와 실행 현황](https://app.notion.com/p/3f00691a0023813d9b26da18f566b92d). Slack 증상/시각과 이 보드의 환경·호스트·시간 범위를 맞춰 확인하세요. Actions 예약 지연·누락과 내부 수집 DOWN을 구분합니다.';
}
fs.mkdirSync(directory, { recursive: true });
for (const [index, board] of boards.entries()) {
  const y = board.panels[0].gridPos.h;
  const needsSeparator = board.panels[1].type !== 'row';
  for (const panel of board.panels.slice(1)) panel.gridPos.y += needsSeparator ? 2 : 1;
  const helpRow = {
    id: 10000, type: 'row', title: '도움말 · 어떤 상황에서 보고 어떻게 점검하나요?', collapsed: true,
    gridPos: { x: 0, y, w: 24, h: 1 },
    panels: [{ id: 10001, type: 'text', title: '증상 → 지표 → 다음 점검',
      gridPos: { x: 0, y: y + 1, w: 24, h: 11 }, options: { mode: 'markdown', content: help[board.uid] } }],
  };
  const separator = { id: 10002, type: 'row', title: '운영 지표', collapsed: false,
    panels: [], gridPos: { x: 0, y: y + 1, w: 24, h: 1 } };
  board.panels.splice(1, 0, helpRow, ...(needsSeparator ? [separator] : []));
  fs.writeFileSync(path.join(directory, destinations[index][2]), `${JSON.stringify(board, null, 2)}\n`);
}
console.log(JSON.stringify(boards.map(board => ({ uid: board.uid, title: board.title, panels: board.panels.length })), null, 2));
