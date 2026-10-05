import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = join(dirname(fileURLToPath(import.meta.url)), '..', 'observability', 'grafana', 'dashboards');
const destinations = [['yanus-overview','01 전체 상태'],['yanus-api','02 API 요청·오류'],['yanus-logs','03 로그'],['yanus-hosts','04 서버 자원'],['yanus-postgres','05 PostgreSQL'],['yanus-traces','06 요청 추적']];
const links = destinations.map(([uid,title])=>({title,type:'link',url:`/d/${uid}`,includeVars:true,keepTime:true,targetBlank:false}));
const prometheus = {type:'prometheus',uid:'yanus-prometheus'};
const tempo = {type:'tempo',uid:'yanus-tempo'};
const help = `### 요청이 느리거나 실패하면 처리 구간을 확인하세요
| 상황 | 확인 순서 | 다음 조치 |
| --- | --- | --- |
| API 지연 증가 | 02에서 URI·발생 시각 확인 → 아래 느린 요청 선택 → HTTP/DB/외부 호출 시간 | 긴 DB 구간과 반복 호출 수를 확인하고 실행 계획·연결 대기를 별도로 조사합니다. |
| 특정 요청의 오류 | 03 로그 상세 TraceID의 '요청 처리 구간 보기' → span 상태·error.type | 'Logs for this span'으로 같은 traceId의 로그를 이어서 확인합니다. |
| 추적이 없음 | 로그 traceFlags·시간 범위·Tempo UP → Alloy/Agent 전송 상태 | 10% 샘플링이므로 모든 요청을 저장하지 않습니다. traceFlags의 최하위 비트가 0인 00/02 등은 미선택 요청입니다. 01/03 등은 선택 요청입니다. |
| 수집기가 중단됨 | Tempo/Alloy 상태·같은 시간의 exporter 오류 → 실제 API | 수집 오류와 서비스 오류를 구분합니다. 제한된 큐/재시도는 영구 저장이 아닙니다. |

**시작 조건:** PROD, 서비스 yanus-backend, 10% 샘플링·48시간 보관입니다. 수집·조회에는 수초 지연이 생길 수 있습니다. 비밀번호·토큰·본문·URL 쿼리·SQL 값/문자열·예외 메시지는 추적 저장에서 제거합니다. DB span은 시간·횟수·작업 종류를 제공하며 개별 SQL은 별도 조사합니다. span 시간은 중첩될 수 있어 합산만으로 전체 지연을 계산하지 않습니다.

**한계:** 단일 모니터링 서버의 로컬 저장 구성이며 HA·영구 증거 저장·전체 요청 기록을 보장하지 않습니다. 장애 사례는 로그·지표·실행 계획과 함께 판단하고 복구 후 같은 조건으로 다시 측정하세요.`;
const board = {
  id:null,uid:'yanus-traces',title:'06 요청 추적',schemaVersion:39,version:1,editable:false,tags:['yanus','prod'],timezone:'Asia/Seoul',refresh:'30s',time:{from:'now-30m',to:'now'},links,
  templating:{list:[{name:'minDuration',label:'느린 요청 기준',type:'custom',query:'100ms,500ms,1s,2s',current:{text:'100ms',value:'100ms'},options:['100ms','500ms','1s','2s'].map(v=>({text:v,value:v,selected:v==='100ms'}))}]},
  panels:[
    {id:1,type:'text',title:'도움말 · 증상별 확인과 조치',gridPos:{x:0,y:0,w:24,h:11},options:{mode:'markdown',content:help}},
    {id:2,type:'stat',title:'Tempo 수집 상태',description:'DOWN이면 모니터링 서버의 tempo service·메모리·디스크부터 확인합니다. UP은 모든 요청이 저장된다는 뜻이 아닙니다.',datasource:prometheus,gridPos:{x:0,y:11,w:6,h:4},targets:[{refId:'A',expr:'up{job="yanus-tempo"}',datasource:prometheus}],fieldConfig:{defaults:{min:0,max:1,mappings:[{type:'value',options:{0:{text:'DOWN',color:'red'},1:{text:'UP',color:'green'}}}],thresholds:{mode:'absolute',steps:[{color:'red',value:null},{color:'green',value:1}]}},overrides:[]},options:{reduceOptions:{calcs:['lastNotNull'],values:false},colorMode:'value'}},
    {id:3,type:'timeseries',title:'수집한 span · 초당',description:'샘플링된 요청의 처리 구간 수입니다. API 요청 수와 같지 않습니다. 0이면 무요청·미선택·전송 중단을 함께 확인합니다.',datasource:prometheus,gridPos:{x:6,y:11,w:18,h:4},targets:[{refId:'A',expr:'sum(rate(tempo_distributor_spans_received_total{job="yanus-tempo"}[$__rate_interval]))',datasource:prometheus}],fieldConfig:{defaults:{unit:'ops'},overrides:[]},options:{legend:{displayMode:'list',placement:'bottom'}}},
    {id:4,type:'table',title:'느린 요청 · TraceID를 선택해 처리 구간 보기',description:'현재 서비스의 샘플링된 요청 중 기준보다 느린 요청을 조회합니다. TraceID를 선택하면 Grafana Explore에서 span 시간·DB 호출·오류와 같은 요청의 로그를 확인할 수 있습니다.',datasource:tempo,gridPos:{x:0,y:15,w:24,h:12},targets:[{refId:'A',datasource:tempo,queryType:'traceql',query:'{ resource.service.name = "yanus-backend" && duration > $minDuration }',limit:20,tableType:'traces'}],fieldConfig:{defaults:{},overrides:[]},options:{showHeader:true}},
  ],
};
await writeFile(join(directory,'06-traces.json'),JSON.stringify(board,null,2)+'\n');
for (const file of ['01-overview.json','02-api.json','03-logs.json','04-hosts.json','05-postgres.json']) {
  const p=join(directory,file); const dashboard=JSON.parse(await readFile(p,'utf8'));
  dashboard.links=[...(dashboard.links||[]).filter(l=>l.url!=='/d/yanus-traces'),links.at(-1)];
  await writeFile(p,JSON.stringify(dashboard,null,2)+'\n');
}
console.log(JSON.stringify({dashboard:'yanus-traces',existing_navigation_updated:5}));
