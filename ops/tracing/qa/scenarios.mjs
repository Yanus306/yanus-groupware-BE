import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createHmac, randomBytes } from 'node:crypto';
import { chmod, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const runFile = promisify(execFile);
const controller = new AbortController();
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => controller.abort());
const owned = [];
const root = await mkdtemp(join(tmpdir(), 'yanus-tracing-qa-'));
await chmod(root, 0o700);
const suffix = randomBytes(6).toString('hex');
const prefix = `yanus-tracing-217-${suffix}`;
const pg = `${prefix}-pg`;
const tempo = `${prefix}-tempo`;
const alloy = `${prefix}-alloy`;
const app = `${prefix}-app`;
const pass = randomBytes(24).toString('hex');
const jwtSecret = randomBytes(48).toString('hex');
const images = {
  tempo: 'grafana/tempo@sha256:3076b8dcdfb32fd6bc5ccef85e7b7313e6199b9cb84366257fc17ecb696db5fd',
  alloy: 'grafana/alloy@sha256:2aa2099af76c0098d4af7a4d6e48f86cb66dc1a000222ad927a1c67c6542d13f',
};
const receipts = [];
async function command(file, args, options = {}) {
  try { return await runFile(file, args, { timeout: 60000, maxBuffer: 4 * 1024 * 1024, signal: controller.signal, ...options }); }
  catch (error) {
    await writeFile(join(root, 'diagnostic.txt'), error.stderr || error.message, { mode: 0o600 });
    throw new Error(file === 'docker' ? 'DOCKER_COMMAND_FAILED' : 'COMMAND_FAILED');
  }
}
async function container(name, args) {
  owned.push(name);
  await command('docker', ['run', '-d', '--name', name, '--label', 'yanus.qa=tracing-217', ...args]);
}
async function curl(path, args = []) {
  return command('docker', ['exec', app, 'curl', '--max-time', '5', '-sS', ...args, path]);
}
function check(value, code) { if (!value) throw new Error(code); }
async function trace(id) {
  for (let i = 0; i < 30; i++) {
    const r = await curl(`http://127.0.0.1:3200/api/traces/${id}`, ['-H', 'Accept: application/json', '-w', '\n%{http_code}']);
    const [body, status] = [r.stdout.slice(0, r.stdout.lastIndexOf('\n')), r.stdout.slice(r.stdout.lastIndexOf('\n') + 1)];
    if (status === '200') return JSON.parse(body);
    await delay(500, undefined, { signal: controller.signal });
  }
  throw new Error('TRACE_NOT_STORED');
}
function spans(value) {
  return (value.batches || value.resourceSpans || []).flatMap(b => (b.scopeSpans || b.instrumentationLibrarySpans || []).flatMap(s => s.spans || []));
}
try {
  const agent = resolve(process.argv[2] || '.omo/downloads/opentelemetry-javaagent.jar');
  const jar = resolve(process.argv[3] || 'build/libs/attendance-0.0.1-SNAPSHOT.jar');
  const overlay = resolve('build/libs/yanus-tracing.jar');
  const { createHash } = await import('node:crypto');
  check(createHash('sha256').update(await readFile(agent)).digest('hex') === 'f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82', 'AGENT_CHECKSUM_MISMATCH');
  await mkdir(join(root, 'logs'), { mode: 0o700 });
  await writeFile(join(root, 'tempo.yml'), (await readFile('ops/tracing/tempo.yml', 'utf8')).replace('127.0.0.1:4318', '127.0.0.1:4319'), { mode: 0o600 });
  await writeFile(join(root, 'alloy.config'), (await readFile('ops/tracing/alloy-tracing.config', 'utf8')).replace('http://MONITORING_ADDRESS:4320', 'http://127.0.0.1:4319').replace('/etc/alloy/tempo-authorization', '/config/password'), { mode: 0o600 });
  await writeFile(join(root, 'password'), `Basic ${Buffer.from('alloy:synthetic-password').toString('base64')}`, { mode: 0o600 });
  await writeFile(join(root, 'otel.properties'), (await readFile('ops/tracing/otel.properties', 'utf8')).replace('otel.traces.sampler.arg=0.1', 'otel.traces.sampler.arg=1.0'), { mode: 0o600 });
  await writeFile(join(root, 'application.properties'), `spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/fixture\nspring.datasource.username=postgres\nspring.datasource.password=${pass}\njwt.secret=${jwtSecret}\nminio.endpoint=http://127.0.0.1:9000\nminio.access-key=fixture\nminio.secret-key=fixture-only\nspring.mail.host=127.0.0.1\nspring.mail.port=9\nspring.mail.username=fixture\nspring.mail.password=fixture\nlogging.level.com.yanus.attendance=INFO\nlogging.structured.format.file=com.yanus.attendance.global.logging.SafeJsonLogFormatter\nlogging.file.name=/qa/logs/app.log\n`, { mode: 0o600 });
  await container(pg, ['--network', 'none', '--tmpfs', '/var/lib/postgresql/data:rw,size=256m', '-e', `POSTGRES_PASSWORD=${pass}`, '-e', 'POSTGRES_DB=fixture', 'postgres:16.15']);
  for (let i = 0; i < 30; i++) {
    try { await command('docker', ['exec', pg, 'pg_isready', '-U', 'postgres']); break; } catch { await delay(500); }
  }
  await container(tempo, ['--user', '0', '--network', `container:${pg}`, '--memory', '512m', '-e', 'GOMEMLIMIT=384MiB', '--tmpfs', '/var/tempo:rw,size=128m', '-v', `${root}/tempo.yml:/etc/tempo/config.yml:ro`, images.tempo, '-config.file=/etc/tempo/config.yml']);
  await container(alloy, ['--network', `container:${pg}`, '--memory', '256m', '--tmpfs', '/tmp:rw,size=128m', '-v', `${root}:/config:ro`, images.alloy, 'run', '--storage.path=/tmp/alloy', '--server.http.listen-addr=127.0.0.1:12345', '/config/alloy.config']);
  await container(app, ['--network', `container:${pg}`, '--memory', '768m', '--read-only', '--tmpfs', '/tmp:rw,size=64m', '-v', `${root}:/qa`, '-v', `${jar}:/app.jar:ro`, '-v', `${agent}:/agent.jar:ro`, '-v', `${overlay}:/overlay/yanus-tracing.jar:ro`, '-e', 'OTEL_JAVAAGENT_CONFIGURATION_FILE=/qa/otel.properties', 'yanus-tracing-qa:217', 'java', '-javaagent:/agent.jar', '-Dloader.path=/overlay', '-Dspring.config.additional-location=file:/qa/application.properties', '-cp', '/app.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher']);
  for (let i = 0; i < 90; i++) {
    try { const r = await curl('http://127.0.0.1:9091/actuator/health/readiness', ['-f']); if (JSON.parse(r.stdout).status === 'UP') break; } catch { if (i === 89) throw new Error('APP_READINESS_FAILED'); }
    await delay(500, undefined, { signal: controller.signal });
  }
  const header = Buffer.from(JSON.stringify({ alg: 'HS384', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ sub: '0', role: 'ADMIN', exp: Math.floor(Date.now()/1000)+600 })).toString('base64url');
  const token = `${header}.${payload}.${createHmac('sha384', jwtSecret).update(`${header}.${payload}`).digest('base64url')}`;
  const id = randomBytes(16).toString('hex');
  const requestId = randomBytes(16).toString('hex');
  const r = await curl('http://127.0.0.1:8080/api/v1/teams?private=fixture-secret', ['-f', '-H', `Authorization: Bearer ${token}`, '-H', `traceparent: 00-${id}-1234567890abcdef-01`, '-H', `X-Request-ID: ${requestId}`]);
  check(JSON.parse(r.stdout).code === 'SUCCESS', 'READ_API_FAILED');
  const stored = await trace(id);
  const found = spans(stored);
  check(found.some(s => Number(s.kind) === 2 || s.kind === 'SPAN_KIND_SERVER'), 'HTTP_SPAN_MISSING');
  check(found.some(s => s.attributes?.some(a => ['db.system.name','db.system'].includes(a.key))), 'JDBC_SPAN_MISSING');
  check(!JSON.stringify(stored).includes('fixture-secret') && !JSON.stringify(stored).includes(token), 'PRIVATE_DATA_LEAK');
  const lines = (await readFile(join(root, 'logs/app.log'), 'utf8')).split('\n').filter(Boolean).map(l => JSON.parse(l));
  check(lines.some(l => l.requestId === requestId && l.traceId === id && /^[0-9a-f]{16}$/.test(l.spanId)
    && l.traceFlags === '01'), 'LOG_CORRELATION_MISSING');
  receipts.push({ scenario: 'actual_http_jdbc_and_log_correlation', status: 'PASS', spans: found.length });
  const rootRequestId = randomBytes(16).toString('hex');
  await curl('http://127.0.0.1:8080/v3/api-docs', ['-f', '-H', `X-Request-ID: ${rootRequestId}`]);
  const rootLine = (await readFile(join(root, 'logs/app.log'), 'utf8')).split('\n').filter(Boolean)
    .map(line => JSON.parse(line)).find(line => line.requestId === rootRequestId);
  check(rootLine && /^[0-9a-f]{2}$/.test(rootLine.traceFlags)
    && (parseInt(rootLine.traceFlags,16)&1)===1, 'ROOT_TRACE_FLAGS_MISSING');
  receipts.push({ scenario: 'agent_generated_root_trace_flags_preserved', status: 'PASS', trace_flags: rootLine.traceFlags });
  const injectedId = randomBytes(16).toString('hex');
  const start = BigInt(Date.now()) * 1000000n;
  const marker = 'sensitive-fixture-marker';
  const body = {resourceSpans:[{resource:{attributes:[{key:'service.name',value:{stringValue:'yanus-fixture'}},{key:'process.command_line',value:{stringValue:marker}}]},scopeSpans:[{spans:[{traceId:injectedId,spanId:'1234567890abcdef',name:marker,kind:3,startTimeUnixNano:String(start),endTimeUnixNano:String(start+1000000n),attributes:[{key:'db.system.name',value:{stringValue:'postgresql'}},{key:'db.query.text',value:{stringValue:marker}},{key:'url.full',value:{stringValue:marker}},{key:'http.request.header.authorization',value:{stringValue:marker}}],status:{code:2,message:marker},events:[{timeUnixNano:String(start),name:marker,attributes:[{key:'exception.message',value:{stringValue:marker}},{key:'exception.stacktrace',value:{stringValue:marker}}]}]}]}]}]};
  await curl('http://127.0.0.1:4318/v1/traces', ['-f', '-H', 'Content-Type: application/json', '--data-binary', JSON.stringify(body)]);
  check(!JSON.stringify(await trace(injectedId)).includes(marker), 'SANITIZER_LEAK');
  receipts.push({ scenario: 'actual_collector_strips_query_headers_exception_resource_and_name', status: 'PASS' });
  const bad = await curl('http://127.0.0.1:8080/v3/api-docs', ['-f','-H','traceparent: malformed-fixture-input']);
  check(JSON.parse(bad.stdout).openapi, 'BAD_TRACE_INPUT_BROKE_APP');
  receipts.push({ scenario: 'malformed_traceparent_does_not_break_api', status: 'PASS' });
  await command('docker',['stop',alloy]);
  const unavailable = await curl('http://127.0.0.1:8080/api/v1/teams', ['-f','-H',`Authorization: Bearer ${token}`]);
  check(JSON.parse(unavailable.stdout).code === 'SUCCESS', 'COLLECTOR_FAILURE_BROKE_APP');
  receipts.push({ scenario: 'collector_down_api_still_works', status: 'PASS' });
  for (const name of owned) {
    const inspected = JSON.parse((await command('docker',['inspect',name])).stdout)[0];
    check(Object.keys(inspected.HostConfig.PortBindings || {}).length === 0, 'PUBLIC_PORT_FOUND');
    check(inspected.HostConfig.NetworkMode === (name === pg ? 'none' : `container:${pg}`)
      || (name !== pg && inspected.HostConfig.NetworkMode.startsWith('container:')), 'UNEXPECTED_NETWORK');
  }
  const proof = { status:'PASS', receipts, public_ports:0, production_connections:0 };
  await mkdir('.omo',{recursive:true});
  await writeFile('.omo/tracing-qa-proof.json', JSON.stringify(proof)+'\n', {mode:0o600});
  console.log(JSON.stringify(proof));
} catch (error) {
  for (const name of owned) {
    try { const r=await runFile('docker',['logs',name],{timeout:10000,maxBuffer:2*1024*1024}); await writeFile(join(root,`${name}.log`),r.stdout+r.stderr,{mode:0o600}); } catch {}
  }
  await mkdir('.omo',{recursive:true});
  const {cp}=await import('node:fs/promises');
  await cp(root,'.omo/tracing-failure',{recursive:true});
  console.error(JSON.stringify({status:'FAIL',code:error.message,private_diagnostics:'.omo/tracing-failure'}));
  process.exitCode=1;
} finally {
  let clean=true;
  for (const name of owned.reverse()) {try {await runFile('docker',['rm','-f','-v',name],{timeout:30000});}catch{clean=false;}}
  await rm(root,{recursive:true,force:true});
  console.log(JSON.stringify({cleanup:clean?'PASS':'FAIL',temporary_removed:true}));
  if(!clean) process.exitCode=1;
}
