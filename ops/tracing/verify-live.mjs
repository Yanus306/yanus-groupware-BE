import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { randomBytes } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import { mkdir, writeFile } from 'node:fs/promises';

const execute=promisify(execFile);
async function ssh(host, command) {
  try {return (await execute('ssh',['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10',host,command],{timeout:15000,maxBuffer:4*1024*1024})).stdout;}
  catch {throw new Error('HOST_VERIFICATION_FAILED');}
}
function check(value,code){if(!value)throw new Error(code);}
try {
  const requests=[];
  for(let i=0;i<40;i++){
    const requestId=randomBytes(16).toString('hex');
    await ssh('app-server',`curl --max-time 3 -fsS -H 'X-Request-ID: ${requestId}' 'http://127.0.0.1:8080/v3/api-docs?private=sensitive-fixture-marker' -o /dev/null`);
    requests.push(requestId);
  }
  for(let i=0;i<30;i++) await ssh('app-server','curl --max-time 3 -fsS http://127.0.0.1:9091/actuator/health/readiness -o /dev/null');
  await delay(3000);
  const logs=JSON.parse(await ssh('app-server',`python3 -c 'import json; from collections import deque; ids=set(${JSON.stringify(requests)}); result=[]; f=open("/var/log/yanus/prod/application.log"); lines=deque(f,3000); f.close();
for line in lines:
 try:
  row=json.loads(line)
  if row.get("requestId") in ids: result.append({key:row.get(key) for key in ["requestId","traceId","spanId","traceFlags","status"]})
 except (ValueError,TypeError): pass
print(json.dumps(result))'`));
  check(logs.length>0,'REQUEST_LOGS_MISSING');
  const sampled=logs.filter(l=>/^[0-9a-f]{2}$/.test(l.traceFlags)
    && (parseInt(l.traceFlags,16)&1)===1 && /^[0-9a-f]{32}$/.test(l.traceId));
  check(sampled.length>0,'NO_SAMPLED_TRACE_OBSERVED');
  let trace;
  for(let i=0;i<15;i++){
    try {trace=JSON.parse(await ssh('monitoring-server',`curl --max-time 3 -fsS -H 'Accept: application/json' http://127.0.0.1:3200/api/traces/${sampled[0].traceId}`));break;}catch{await delay(500);}
  }
  check(trace,'TRACE_LOOKUP_FAILED');
  const serialized=JSON.stringify(trace);
  check(!serialized.includes('sensitive-fixture-marker'),'PRIVATE_QUERY_LEAK');
  const query=encodeURIComponent('{ resource.service.name = "yanus-backend" && (span.db.system.name = "postgresql" || span.db.system = "postgresql") }');
  const search=JSON.parse(await ssh('monitoring-server',`curl --max-time 5 -fsS 'http://127.0.0.1:3200/api/search?q=${query}&limit=5'`));
  check(search.traces?.length>0,'LIVE_JDBC_TRACE_MISSING');
  const result={status:'PASS',request_logs:logs.length,sampled_logs:sampled.length,trace_id:sampled[0].traceId,span_id:sampled[0].spanId,request_id:sampled[0].requestId,http_status:sampled[0].status,jdbc_search_matches:search.traces.length,query_removed:true};
  await mkdir('.omo',{recursive:true});
  await writeFile('.omo/tracing-live-proof.json',JSON.stringify(result)+'\n',{mode:0o600});
  console.log(JSON.stringify(result));
}catch(error){console.error(JSON.stringify({status:'FAIL',code:error.message}));process.exitCode=1;}
