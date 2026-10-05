import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { createHash } from 'node:crypto';
import { chmod, copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import assert from 'node:assert/strict';
import { unpackArchive } from '../archive.mjs';
import { restoreDrill, validateDrillConfig } from '../restore-drill.mjs';

const execute = promisify(execFile);
const [root, id, identity, jar] = process.argv.slice(2);
const source = { root: resolve(root), id, identity: resolve(identity), jar: resolve(jar) };
validateDrillConfig(source);
const fixture = await mkdtemp(join(tmpdir(), 'yanus-restore-negative-'));
await chmod(fixture, 0o700);
const config = { ...source, root: fixture };
const results = [];
const controller = new AbortController();
let activeChild;
const cancel = () => { controller.abort(); activeChild?.kill('SIGTERM'); };
process.on('SIGTERM', cancel); process.on('SIGINT', cancel);
const run = (command, args) => execute(command, args, { timeout: 180_000, maxBuffer: 1024 * 1024, signal: controller.signal });
const expectFailure = (promise, reason) => assert.rejects(promise, error => {
  assert.match(error.message, reason);
  assert.match(error.outcome?.cleanup || '', /임시 평문·설정 제거/);
  return true;
});
const restoreContainers = async () => (await run('docker', ['ps', '-aq', '--filter', 'label=yanus.restore.drill'])).stdout.trim();
const originalContainers = await restoreContainers();
try {
  await copyFile(join(source.root, `${id}.json`), join(fixture, `${id}.json`));
  await copyFile(join(source.root, `${id}.age`), join(fixture, `${id}.age`));
  const originalMetadata = JSON.parse(await readFile(join(fixture, `${id}.json`), 'utf8'));
  assert.throws(() => validateDrillConfig({ ...config, target: 'prod' }), /ISOLATED_TARGET_ONLY/);
  results.push('운영 대상 입력 거절 PASS');

  await run('age-keygen', ['-o', join(fixture, 'wrong-key.txt')]);
  await chmod(join(fixture, 'wrong-key.txt'), 0o644);
  await expectFailure(restoreDrill({ ...config, identity: join(fixture, 'wrong-key.txt') }), /UNPROTECTED_IDENTITY/);
  results.push('그룹/다른 사용자에게 열린 개인키 권한 거절 PASS');
  await chmod(join(fixture, 'wrong-key.txt'), 0o600);
  await expectFailure(restoreDrill({ ...config, identity: join(fixture, 'wrong-key.txt') }), /DECRYPT_FAILED/);
  results.push('잘못된 복호화키 거절·평문 정리 PASS');
  await writeFile(join(fixture, `${id}.age`), 'damaged fixture archive');
  await expectFailure(restoreDrill(config), /ARCHIVE_CHECKSUM_MISMATCH/);
  results.push('외부 archive 손상 거절 PASS');

  const decrypted = join(fixture, 'original.tar');
  await run('age', ['-d', '-i', source.identity, '-o', decrypted, join(source.root, `${id}.age`)]);
  const { entries, manifest } = unpackArchive(await readFile(decrypted), id);
  const contents = join(fixture, 'contents');
  await mkdir(contents, { mode: 0o700 });
  const recipient = (await run('age-keygen', ['-y', source.identity])).stdout.trim();
  const buildBrokenArchive = async changes => {
    const hashes = {};
    for (const name of ['database.dump', 'roles.sql', 'verification.json']) {
      const data = changes[name] || entries.get(name);
      await writeFile(join(contents, name), data, { mode: 0o600 });
      hashes[name] = createHash('sha256').update(data).digest('hex');
    }
    await writeFile(join(contents, 'manifest.json'), JSON.stringify({ ...manifest, files: hashes }), { mode: 0o600 });
    const tar = join(fixture, 'broken.tar');
    await run('tar', ['--format=ustar', '-cf', tar, '-C', contents, 'database.dump', 'roles.sql', 'verification.json', 'manifest.json']);
    await rm(join(fixture, `${id}.age`));
    await run('age', ['-r', recipient, '-o', join(fixture, `${id}.age`), tar]);
    const payload = await readFile(join(fixture, `${id}.age`));
    await writeFile(join(fixture, `${id}.json`), JSON.stringify({ ...originalMetadata, bytes: payload.length,
      sha256: createHash('sha256').update(payload).digest('hex') }), { mode: 0o600 });
  };
  await buildBrokenArchive({ 'database.dump': Buffer.from('invalid PostgreSQL custom dump fixture') });
  await expectFailure(restoreDrill(config), /DRILL_COMMAND_FAILED:restore/);
  results.push('내부 hash는 맞지만 유효하지 않은 dump의 실제 pg_restore 실패·정리 PASS');
  const verification = JSON.parse(entries.get('verification.json').toString('utf8'));
  await buildBrokenArchive({ 'roles.sql': Buffer.from('-- owner role deliberately missing\n'),
    'verification.json': Buffer.from(JSON.stringify({ ...verification, database_owner: 'missing_fixture_owner' })) });
  await expectFailure(restoreDrill(config), /DRILL_COMMAND_FAILED:createdb/);
  results.push('소유자 역할 누락 실제 DB 생성 거절·정리 PASS');

  await copyFile(join(source.root, `${id}.json`), join(fixture, `${id}.json`));
  await copyFile(join(source.root, `${id}.age`), join(fixture, `${id}.age`));
  const child = spawn(process.execPath, ['ops/backup/restore-drill.mjs', fixture, id, source.identity, source.jar], { stdio: 'ignore' });
  activeChild = child;
  const exited = new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', code => resolveExit(code)); });
  const timeout = setTimeout(() => child.kill('SIGTERM'), 30_000);
  try {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (await restoreContainers() !== originalContainers) { child.kill('SIGTERM'); break; }
      if (child.exitCode !== null) break;
      await new Promise(resolveWait => setTimeout(resolveWait, 100));
    }
    assert.notEqual(await exited, 0);
  } finally {
    clearTimeout(timeout);
    if (child.exitCode === null) child.kill('SIGTERM');
    await exited;
    activeChild = undefined;
  }
  assert.equal(await restoreContainers(), originalContainers);
  results.push('복원 중 SIGTERM 취소 후 컨테이너/임시 평문 정리 PASS');
  console.log(JSON.stringify({ status: 'SUCCESS', scenarios: results, cleanup: '격리 복원 컨테이너 없음·원본 암호화 사본 유지' }, null, 2));
} finally {
  await rm(fixture, { recursive: true, force: true });
  process.removeListener('SIGTERM', cancel); process.removeListener('SIGINT', cancel);
}
