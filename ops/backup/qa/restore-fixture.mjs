import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { randomBytes } from 'node:crypto';
import { chmod, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import assert from 'node:assert/strict';
import { restoreDrill } from '../restore-drill.mjs';

// CI uses generated records and a disposable key; customer archives and SSH are never inputs.
const execute = promisify(execFile);
const jar = resolve(process.argv[2] || 'build/libs/attendance-0.0.1-SNAPSHOT.jar');
const temporary = await mkdtemp(join(tmpdir(), 'yanus-restore-fixture-'));
await chmod(temporary, 0o700);
const nonce = randomBytes(8).toString('hex');
const database = `yanus-restore-fixture-db-${nonce}`;
const application = `yanus-restore-fixture-app-${nonce}`;
const containers = [];
const controller = new AbortController();
const cancel = () => controller.abort();
process.on('SIGTERM', cancel); process.on('SIGINT', cancel);
const run = (...args) => execute('docker', args, { timeout: 180_000, maxBuffer: 8 * 1024 * 1024, signal: controller.signal });
const shell = async command => (await run('exec', database, 'bash', '-ceu', command)).stdout.trim();
let outcome;
try {
  const identity = join(temporary, 'fixture-identity.txt');
  await execute('age-keygen', ['-o', identity]);
  await chmod(identity, 0o600);
  const recipient = (await execute('age-keygen', ['-y', identity])).stdout.trim();
  const root = join(temporary, 'archives');
  await mkdir(root, { mode: 0o700 });
  const configuration = join(temporary, 'fixture.properties');
  await writeFile(configuration, `spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/yanus
spring.datasource.username=yanus
spring.datasource.password=fixture-only
spring.jpa.hibernate.ddl-auto=validate
jwt.secret=${randomBytes(32).toString('hex')}
minio.endpoint=http://127.0.0.1:9000
minio.access-key=fixture-only
minio.secret-key=fixture-only-secret
spring.mail.host=127.0.0.1
spring.mail.port=9
spring.mail.username=fixture-only
spring.mail.password=fixture-only
server.address=127.0.0.1
server.port=18080
management.server.port=18081
logging.level.root=ERROR
`, { mode: 0o600 });
  await run('build', '-f', 'ops/backup/qa/restore.Dockerfile', '-t', 'yanus-restore-qa:213', '.');
  containers.push(database);
  await run('run', '-d', '--name', database, '--label', `yanus.restore.fixture=${nonce}`, '--network', 'none',
    '--memory', '512m', '--tmpfs', '/var/lib/postgresql/data:rw,size=256m',
    '-e', 'POSTGRES_DB=yanus', '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'yanus-restore-qa:213');
  await shell('for ((i=0;i<100;i++)); do pg_isready -h 127.0.0.1 -q && exit 0; sleep 0.2; done; exit 1');
  await run('exec', database, 'psql', '-Xq', '-U', 'postgres', '-v', 'ON_ERROR_STOP=1', '-d', 'yanus', '-c',
    'CREATE ROLE yanus LOGIN; ALTER DATABASE yanus OWNER TO yanus; GRANT USAGE, CREATE ON SCHEMA public TO yanus;');
  containers.push(application);
  await run('run', '-d', '--name', application, '--label', `yanus.restore.fixture=${nonce}`, '--network', `container:${database}`,
    '--memory', '768m', '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m',
    '--mount', `type=bind,source=${jar},target=/app.jar,readonly`,
    '--mount', `type=bind,source=${configuration},target=/fixture.properties,readonly`,
    'eclipse-temurin:21-jre', 'java', '-Xmx384m', '-jar', '/app.jar', '--spring.config.additional-location=file:/fixture.properties');
  await shell('for ((i=0;i<120;i++)); do curl -fsS --max-time 2 http://127.0.0.1:18081/actuator/health/readiness >/dev/null 2>&1 && exit 0; sleep 0.5; done; exit 1');
  await run('rm', '-f', '-v', application);
  containers.pop();
  await run('exec', database, 'psql', '-Xq', '-U', 'yanus', '-v', 'ON_ERROR_STOP=1', '-d', 'yanus', '-c',
    "INSERT INTO team(name,created_at) VALUES('복원 훈련 팀 A',now()),('복원 훈련 팀 B',now());");
  const config = { database: 'yanus', spool: '/var/backups/yanus-db', runtime: '/run/yanus-backup',
    state: '/var/lib/yanus-backup', metrics: '/var/lib/prometheus/node-exporter', recipient, retention_days: 14 };
  const configFile = join(temporary, 'config.json');
  await writeFile(configFile, JSON.stringify(config), { mode: 0o600 });
  await run('exec', database, 'install', '-d', '-m', '0700', '/etc/yanus-backup');
  await run('cp', configFile, `${database}:/etc/yanus-backup/config.json`);
  await run('exec', database, 'chown', 'root:root', '/etc/yanus-backup/config.json');
  await run('exec', database, 'chmod', '600', '/etc/yanus-backup/config.json');
  const backup = JSON.parse(await shell('/usr/local/libexec/yanus-backup/backup.sh'));
  assert.equal(backup.status, 'SUCCESS');
  const state = JSON.parse(await shell('cat /var/lib/yanus-backup/local.json'));
  for (const extension of ['age', 'json']) {
    const target = join(root, `${state.id}.${extension}`);
    await run('cp', `${database}:/var/backups/yanus-db/${state.id}.${extension}`, target);
    await chmod(target, 0o600);
  }
  await run('rm', '-f', '-v', database);
  containers.pop();
  const restored = await restoreDrill({ root, id: state.id, identity, jar });
  assert.equal(restored.status, 'SUCCESS');
  assert.equal(restored.flyway_verified, 27);
  const negative = await execute(process.execPath,
    ['ops/backup/qa/restore-negative.mjs', root, state.id, identity, jar], { timeout: 180_000, maxBuffer: 1024 * 1024, signal: controller.signal });
  outcome = { scope: '고객 데이터·운영 키·SSH 없는 CI fixture', restored, negative: JSON.parse(negative.stdout) };
} finally {
  const failures = [];
  for (const container of containers.reverse()) {
    try { await execute('docker', ['rm', '-f', '-v', container], { timeout: 30_000 }); }
    catch (error) { if (!/No such container/.test(error.stderr || '')) failures.push(container); }
  }
  await rm(temporary, { recursive: true, force: true });
  process.removeListener('SIGTERM', cancel); process.removeListener('SIGINT', cancel);
  if (failures.length) throw new Error('FIXTURE_CLEANUP_FAILED');
}
console.log(JSON.stringify({ ...outcome, fixture_cleanup: 'fixture key·사본·평문·DB·앱 제거 / 공개 포트 없음' }, null, 2));
