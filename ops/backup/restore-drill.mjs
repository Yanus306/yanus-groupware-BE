import { execFile } from 'node:child_process';
import { promisify, isDeepStrictEqual } from 'node:util';
import { createHmac, randomBytes } from 'node:crypto';
import { chmod, lstat, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL } from 'node:url';
import { parseMetadata, sha256, ID_PATTERN } from './metadata.mjs';
import { unpackArchive } from './archive.mjs';

const execute = promisify(execFile);
const identifier = value => `"${value.replaceAll('"', '""')}"`;
export function validateDrillConfig(config) {
  if (Object.keys(config).some(key => !['root', 'id', 'identity', 'jar'].includes(key))
      || !ID_PATTERN.test(config.id) || !config.root || !config.identity || !config.jar) throw new Error('ISOLATED_TARGET_ONLY');
}

export async function restoreDrill(config) {
  validateDrillConfig(config);
  const started = Date.now();
  const steps = {};
  let phase = 'input';
  const controller = new AbortController();
  const cancel = () => controller.abort();
  process.on('SIGTERM', cancel); process.on('SIGINT', cancel);
  const run = async (...args) => {
    try { return await execute('docker', args, { timeout: 180_000, maxBuffer: 8 * 1024 * 1024, signal: controller.signal }); }
    catch (error) {
      await writeFile(join(resolve(config.root), 'restore-diagnostic.json'), JSON.stringify({ phase, operation: args[0], stderr: error.stderr || '', at: new Date().toISOString() }), { mode: 0o600 });
      throw new Error(`DRILL_COMMAND_FAILED:${phase}`, { cause: error });
    }
  };
  const temporary = await mkdtemp(join(tmpdir(), 'yanus-restore-'));
  await chmod(temporary, 0o700);
  const nonce = randomBytes(8).toString('hex');
  const databaseContainer = `yanus-restore-db-${nonce}`;
  const appContainer = `yanus-restore-app-${nonce}`;
  let databaseCreated = false;
  let appCreated = false;
  let outcome;
  try {
    const root = resolve(config.root);
    const metadata = parseMetadata(await readFile(join(root, `${config.id}.json`), 'utf8'));
    const encrypted = join(root, `${config.id}.age`);
    const info = await lstat(encrypted);
    const key = await lstat(config.identity);
    if (metadata.id !== config.id || !info.isFile() || info.isSymbolicLink() || info.size !== metadata.bytes || await sha256(encrypted) !== metadata.sha256) throw new Error('ARCHIVE_CHECKSUM_MISMATCH');
    if (!key.isFile() || key.isSymbolicLink() || (key.mode & 0o077) !== 0) throw new Error('UNPROTECTED_IDENTITY');
    const jar = resolve(config.jar);
    if (!(await lstat(jar)).isFile() || jar.endsWith('-plain.jar')) throw new Error('BOOT_JAR_REQUIRED');
    steps.input_ms = Date.now() - started;
    phase = 'decrypt';
    const decrypted = join(temporary, 'decrypted.tar');
    const decryptStarted = Date.now();
    try { await execute('age', ['-d', '-i', resolve(config.identity), '-o', decrypted, encrypted], { timeout: 60_000, signal: controller.signal }); }
    catch { throw new Error('DECRYPT_FAILED'); }
    await chmod(decrypted, 0o600);
    if ((await lstat(decrypted)).size > 128 * 1024 * 1024) throw new Error('INVALID_ARCHIVE_SIZE');
    const { entries, manifest, verification } = unpackArchive(await readFile(decrypted), config.id);
    if (verification.snapshot_epoch !== metadata.snapshot_epoch) throw new Error('SNAPSHOT_MISMATCH');
    for (const [name, bytes] of entries) await writeFile(join(temporary, name), bytes, { mode: 0o600, flag: 'wx' });
    // Preserve the original bootstrap grantor; only its already-created CREATE statement is omitted.
    const roles = entries.get('roles.sql').toString('utf8');
    await writeFile(join(temporary, 'roles.sql'), roles.replace(/^CREATE ROLE postgres;\r?\n/m, ''), { mode: 0o600 });
    await rm(decrypted);
    steps.decrypt_validate_ms = Date.now() - decryptStarted;
    phase = 'prepare';
    const prepareStarted = Date.now();
    await run('build', '-f', 'ops/backup/qa/restore.Dockerfile', '-t', 'yanus-restore-qa:213', '.');
    databaseCreated = true;
    await run('run', '-d', '--name', databaseContainer, '--label', `yanus.restore.drill=${nonce}`, '--network', 'none',
      '--memory', '512m', '--tmpfs', '/var/lib/postgresql/data:rw,size=256m', '--tmpfs', '/tmp:rw,noexec,nosuid,size=128m',
      '--mount', `type=bind,source=${temporary},target=/backup-input,readonly`,
      '-e', 'POSTGRES_USER=postgres', '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'yanus-restore-qa:213');
    const inspection = JSON.parse((await run('inspect', databaseContainer)).stdout)[0];
    if (inspection.Config.Labels['yanus.restore.drill'] !== nonce || inspection.HostConfig.NetworkMode !== 'none'
        || Object.keys(inspection.HostConfig.PortBindings || {}).length) throw new Error('UNSAFE_TARGET');
    await run('exec', databaseContainer, 'bash', '-ceu', 'for ((i=0;i<100;i++)); do pg_isready -h 127.0.0.1 -U postgres -q && exit 0; sleep 0.2; done; exit 1');
    const sql = async (statement, user = 'postgres') => (await run('exec', databaseContainer,
      'psql', '-XqAt', '-U', user, '-v', 'ON_ERROR_STOP=1', '-d', manifest.database, '-c', statement)).stdout.trim();
    phase = 'roles';
    await run('exec', databaseContainer, 'psql', '-Xq', '-U', 'postgres', '-v', 'ON_ERROR_STOP=1', '-f', '/backup-input/roles.sql');
    phase = 'createdb';
    await run('exec', databaseContainer, 'createdb', '-U', 'postgres', '-O', verification.database_owner, manifest.database);
    steps.prepare_roles_ms = Date.now() - prepareStarted;
    phase = 'restore';
    const restoreStarted = Date.now();
    await run('exec', databaseContainer, 'pg_restore', '--exit-on-error', '-U', 'postgres', '-d', manifest.database, '/backup-input/database.dump');
    steps.restore_ms = Date.now() - restoreStarted;
    phase = 'verify';
    const verifyStarted = Date.now();
    for (const table of verification.tables) {
      if (Number(await sql(`SELECT count(*) FROM ${identifier(table.schema)}.${identifier(table.table)}`)) !== table.rows) throw new Error('ROW_COUNT_MISMATCH');
    }
    const history = JSON.parse(await sql("SELECT coalesce(jsonb_agg(t ORDER BY installed_rank),'[]'::jsonb) FROM public.flyway_schema_history t"));
    if (!isDeepStrictEqual(history, verification.history) || history.some(row => !row.success)) throw new Error('FLYWAY_MISMATCH');
    const constraints = JSON.parse(await sql("SELECT coalesce(json_agg(json_build_object('table',conrelid::regclass::text,'name',conname,'definition',pg_get_constraintdef(oid)) ORDER BY conname),'[]'::json) FROM pg_constraint WHERE connamespace='public'::regnamespace"));
    const ordered = items => items.toSorted((a, b) => `${a.table}.${a.name}`.localeCompare(`${b.table}.${b.name}`));
    if (!isDeepStrictEqual(ordered(constraints), ordered(verification.constraints))) throw new Error('CONSTRAINT_MISMATCH');
    const ownership = JSON.parse(await sql("SELECT coalesce(json_agg(json_build_object('schema',n.nspname,'name',c.relname,'kind',c.relkind,'owner',pg_get_userbyid(c.relowner),'acl',coalesce(c.relacl::text,'')) ORDER BY c.relname),'[]'::json) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m')"));
    if (!isDeepStrictEqual(ownership, verification.ownership)
        || await sql('SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=current_database()') !== verification.database_owner) throw new Error('OWNER_ACL_MISMATCH');
    const sequenceSql = (await run('exec', databaseContainer, 'bash', '-ceu',
      'umask 077; trap "rm -f /tmp/drill-sequences.list" EXIT; pg_restore --list /backup-input/database.dump | sed -n "/ SEQUENCE SET /p" > /tmp/drill-sequences.list; pg_restore --data-only --use-list=/tmp/drill-sequences.list --file=- /backup-input/database.dump')).stdout;
    let sequencesVerified = 0;
    for (const line of sequenceSql.split('\n').filter(value => value.startsWith('SELECT pg_catalog.setval('))) {
      const match = /^SELECT pg_catalog\.setval\('public\.([a-z_][a-z0-9_]*)', (-?\d+), (true|false)\);$/.exec(line);
      if (!match) throw new Error('INVALID_SEQUENCE_VERIFICATION');
      if (await sql(`SELECT last_value::text || ',' || is_called::text FROM public.${identifier(match[1])}`) !== `${match[2]},${match[3]}`) throw new Error('SEQUENCE_VALUE_MISMATCH');
      sequencesVerified++;
    }
    if (sequencesVerified !== verification.ownership.filter(item => item.kind === 'S').length) throw new Error('SEQUENCE_INVENTORY_MISMATCH');
    const password = randomBytes(32).toString('hex');
    await sql(`CREATE ROLE yanus_restore_reader LOGIN PASSWORD '${password}'; GRANT CONNECT ON DATABASE ${identifier(manifest.database)} TO yanus_restore_reader; GRANT USAGE ON SCHEMA public TO yanus_restore_reader; GRANT SELECT ON ALL TABLES IN SCHEMA public TO yanus_restore_reader; ALTER ROLE yanus_restore_reader SET default_transaction_read_only=on;`);
    if (await sql('SHOW transaction_read_only', 'yanus_restore_reader') !== 'on') throw new Error('READER_NOT_READ_ONLY');
    let blocked = false;
    try { await sql('DELETE FROM public.flyway_schema_history WHERE false', 'yanus_restore_reader'); }
    catch (error) { blocked = /cannot execute DELETE in a read-only transaction|permission denied for table/.test(error.cause?.stderr || ''); }
    if (!blocked) throw new Error('READ_ONLY_GUARD_FAILED');
    steps.verify_database_ms = Date.now() - verifyStarted;
    phase = 'api';
    const apiStarted = Date.now();
    const jwt = randomBytes(32).toString('hex');
    await writeFile(join(temporary, 'drill.properties'), `spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/${manifest.database}
spring.datasource.username=yanus_restore_reader
spring.datasource.password=${password}
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=validate
spring.datasource.hikari.read-only=true
jwt.secret=${jwt}
minio.endpoint=http://127.0.0.1:9000
minio.access-key=drill-fixture
minio.secret-key=drill-fixture-secret
spring.mail.host=127.0.0.1
spring.mail.port=9
spring.mail.username=drill-fixture
spring.mail.password=drill-fixture
server.address=127.0.0.1
server.port=18080
management.server.port=18081
logging.level.root=ERROR
logging.level.com.yanus.attendance=ERROR
`, { mode: 0o600 });
    appCreated = true;
    await run('run', '-d', '--name', appContainer, '--label', `yanus.restore.drill=${nonce}`, '--network', `container:${databaseContainer}`,
      '--memory', '768m', '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m',
      '--mount', `type=bind,source=${jar},target=/app.jar,readonly`,
      '--mount', `type=bind,source=${join(temporary, 'drill.properties')},target=/drill.properties,readonly`,
      'eclipse-temurin:21-jre', 'java', '-Xmx384m', '-jar', '/app.jar', '--spring.config.additional-location=file:/drill.properties');
    await run('exec', databaseContainer, 'bash', '-ceu', 'for ((i=0;i<120;i++)); do curl -fsS --max-time 2 http://127.0.0.1:18081/actuator/health/readiness >/dev/null 2>&1 && exit 0; sleep 0.5; done; exit 1');
    const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const tokenBody = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: '0', role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 120 })}`;
    const token = `${tokenBody}.${createHmac('sha256', jwt).update(tokenBody).digest('base64url')}`;
    const response = JSON.parse((await run('exec', databaseContainer, 'curl', '-fsS', '--max-time', '10',
      '-H', `Authorization: Bearer ${token}`, 'http://127.0.0.1:18080/api/v1/teams')).stdout);
    const team = verification.tables.find(table => table.table === 'team');
    if (response.code !== 'SUCCESS' || !Array.isArray(response.data) || !team || response.data.length !== team.rows) throw new Error('READ_API_MISMATCH');
    const unauthorized = (await run('exec', databaseContainer, 'curl', '-s', '-o', '/dev/null', '-w', '%{http_code}', '--max-time', '10', 'http://127.0.0.1:18080/api/v1/teams')).stdout;
    if (!['401', '403'].includes(unauthorized)) throw new Error('AUTH_GUARD_FAILED');
    steps.verify_read_api_ms = Date.now() - apiStarted;
    outcome = { status: 'SUCCESS', backup_id: config.id, snapshot_epoch: metadata.snapshot_epoch,
      data_age_at_start_seconds: Math.max(0, Math.floor(started / 1000) - metadata.snapshot_epoch),
      snapshot_clock_ahead_seconds: Math.max(0, metadata.snapshot_epoch - Math.floor(started / 1000)),
      tables_verified: verification.tables.length, flyway_verified: history.length,
      ownership_verified: ownership.length, constraints_verified: constraints.length, sequences_verified: sequencesVerified,
      api: 'GET /api/v1/teams 200·건수 일치 / 무인증 거절', reader_write: 'BLOCKED',
      jar_sha256: await sha256(jar), postgres_major: 16, steps, total_ms_before_cleanup: Date.now() - started };
  } catch (error) {
    outcome = { status: 'FAILED', phase, reason: /^([A-Z_]+)(:[a-z]+)?$/.test(error.message) ? error.message : 'DRILL_FAILED' };
    throw Object.assign(new Error(`${outcome.reason}:${phase}`), { outcome });
  } finally {
    const cleanupStarted = Date.now();
    const cleanupFailures = [];
    for (const [name, created] of [[appContainer, appCreated], [databaseContainer, databaseCreated]]) {
      if (created) {
        try { await execute('docker', ['rm', '-f', '-v', name], { timeout: 30_000 }); }
        catch (error) { if (!/No such container/i.test(error.stderr || '')) cleanupFailures.push(name); }
      }
    }
    try { await rm(temporary, { recursive: true, force: true }); }
    catch { cleanupFailures.push('temporary-plaintext'); }
    process.removeListener('SIGTERM', cancel); process.removeListener('SIGINT', cancel);
    if (cleanupFailures.length) throw Object.assign(new Error('CLEANUP_FAILED'), { outcome: { status: 'FAILED', cleanup: '정리 미완료', pending: cleanupFailures } });
    if (outcome) { outcome.cleanup = '컨테이너·tmpfs 데이터·임시 평문·설정 제거 / 공개 포트 없음'; outcome.cleanup_ms = Date.now() - cleanupStarted; }
  }
  return outcome;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [root, id, identity, jar, extra] = process.argv.slice(2);
  if (extra) throw new Error('ISOLATED_TARGET_ONLY');
  try { console.log(JSON.stringify(await restoreDrill({ root, id, identity, jar }), null, 2)); }
  catch (error) { console.error(JSON.stringify(error.outcome || { status: 'FAILED', reason: error.message })); process.exitCode = 1; }
}
