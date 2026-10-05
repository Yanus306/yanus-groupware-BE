import fs from 'node:fs/promises';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { jarMetadata, digest } from '../jar-metadata.mjs';
import { verifyRelease } from '../verify-release.mjs';

const execute = promisify(execFile);
const tools = '/usr/local/lib/yanus-deploy';
await fs.cp('/source/ops/deploy', `${tools}/deploy`, { recursive: true });
const baseline = JSON.parse(await fs.readFile('/etc/yanus-deploy/baseline.json', 'utf8'));
assert.equal(baseline.environment, 'qa');
const commit = baseline.runningCommit;
const incoming = `/home/yanus/incoming/${commit}`;
const originalJar = '/source/build/libs/attendance-0.0.1-SNAPSHOT.jar';
const timeline = [];
const start = performance.now();
async function record(scenario, work) {
  const before = new Date().toISOString(); const began = performance.now();
  const result = await work();
  timeline.push({ scenario, startedAt: before, finishedAt: new Date().toISOString(), elapsedMs: Math.round(performance.now() - began), ...result });
  console.log(JSON.stringify(timeline.at(-1)));
}
async function candidate() {
  await fs.copyFile(originalJar, `${incoming}/app.jar`);
  await execute('node', [`${tools}/deploy/create-manifest.mjs`, `${incoming}/app.jar`, `${incoming}/manifest.json`], { env: { ...process.env, GITHUB_SHA: commit } });
}
async function deploy() {
  try { const result = await execute('bash', [`${tools}/deploy/deploy.sh`, incoming], { timeout: 360000, maxBuffer: 1048576 }); return { exit: 0, ...result }; }
  catch (error) { return { exit: error.code, stdout: error.stdout, stderr: error.stderr }; }
}
async function pid() { return (await execute('systemctl', ['show', 'yanus', '--property=MainPID', '--value'])).stdout.trim(); }
await record('checksum preflight preserves process and pointer', async () => {
  await candidate(); const before = await pid(); const pointer = await fs.readlink('/home/yanus/app/app.jar');
  const manifest = JSON.parse(await fs.readFile(`${incoming}/manifest.json`, 'utf8')); manifest.sha256 = '0'.repeat(64);
  await fs.writeFile(`${incoming}/manifest.json`, JSON.stringify(manifest));
  const result = await deploy(); assert.notEqual(result.exit, 0); assert.match(result.stderr, /CHECKSUM_MISMATCH/);
  assert.equal(await pid(), before); assert.equal(await fs.readlink('/home/yanus/app/app.jar'), pointer);
  return { outcome: 'PREFLIGHT_BLOCKED', processPreserved: true };
});
await record('schema change refused before replacement', async () => {
  await candidate(); const before = await pid();
  const manifest = JSON.parse(await fs.readFile(`${incoming}/manifest.json`, 'utf8')); manifest.schemaPolicy = 'unreviewed';
  await fs.writeFile(`${incoming}/manifest.json`, JSON.stringify(manifest));
  const result = await deploy(); assert.match(result.stderr, /SCHEMA_REVIEW_REQUIRED/); assert.equal(await pid(), before);
  return { outcome: 'PREFLIGHT_BLOCKED', processPreserved: true };
});
await record('host flock rejects concurrent deployment', async () => {
  const child = spawn('flock', ['/var/lock/yanus-deploy.lock', 'sh', '-c', 'printf locked; sleep 3'], { stdio: ['ignore', 'pipe', 'pipe'] });
  await new Promise(resolve => child.stdout.once('data', resolve));
  const result = await deploy(); assert.equal(result.exit, 75); assert.match(result.stderr, /DEPLOYMENT_LOCKED/);
  await new Promise(resolve => child.once('close', resolve));
  return { outcome: 'DEPLOYMENT_LOCKED' };
});
async function badJar() {
  await candidate();
  const dir = await fs.mkdtemp('/tmp/yanus-badjar-');
  await fs.mkdir(`${dir}/META-INF`);
  const { stdout } = await execute('unzip', ['-p', `${incoming}/app.jar`, 'META-INF/MANIFEST.MF']);
  await fs.writeFile(`${dir}/META-INF/MANIFEST.MF`, stdout.replace(/Start-Class: [^\r\n]+/, 'Start-Class: fixture.missing.Main'));
  await execute('zip', ['-q', `${incoming}/app.jar`, 'META-INF/MANIFEST.MF'], { cwd: dir });
  await fs.rm(dir, { recursive: true });
  await execute('node', [`${tools}/deploy/create-manifest.mjs`, `${incoming}/app.jar`, `${incoming}/manifest.json`], { env: { ...process.env, GITHUB_SHA: commit } });
}
await record('real invalid JAR process restores previous release and settings', async () => {
  await badJar(); const pointer = await fs.readlink('/home/yanus/app/app.jar');
  const before = await pid(); const propertyHash = digest(await fs.readFile('/opt/yanus-observability/application-observability.properties'));
  const result = await deploy(); assert.notEqual(result.exit, 0); assert.match(result.stdout, /"status":"ROLLED_BACK"/);
  assert.equal(await fs.readlink('/home/yanus/app/app.jar'), pointer); assert.notEqual(await pid(), before);
  assert.equal(digest(await fs.readFile('/opt/yanus-observability/application-observability.properties')), propertyHash);
  assert.equal((await jarMetadata('/home/yanus/app/app.jar')).sha256, baseline.sha256);
  return { outcome: 'ROLLED_BACK', processRestarted: true, configurationRestored: true, exit: result.exit };
});
await record('corrupted retained previous release ends with bounded rollback failure', async () => {
  await badJar(); const pointer = await fs.readlink('/home/yanus/app/app.jar');
  const operation = deploy();
  const until = performance.now() + 15000;
  while (await fs.readlink('/home/yanus/app/app.jar') === pointer) { assert.ok(performance.now() < until); await delay(100); }
  await fs.writeFile(pointer, 'QA damaged retained previous artifact');
  try {
    const result = await operation; assert.notEqual(result.exit, 0); assert.match(result.stdout, /"status":"ROLLBACK_FAILED"/);
    return { outcome: 'ROLLBACK_FAILED', exit: result.exit, automaticRetryEnded: true };
  } finally {
    await fs.copyFile(originalJar, pointer);
    await execute('systemctl', ['restart', 'yanus']);
    const ready = await verifyRelease({ management: 'http://127.0.0.1:9091', main: 'http://127.0.0.1:8080', commit });
    assert.equal(ready.ok, true, 'fixture manual recovery must succeed');
  }
});
await fs.mkdir('/source-evidence', { recursive: true });
await fs.writeFile('/source-evidence/scenarios.json', JSON.stringify({ environment: 'isolated Ubuntu24.04 systemd/PostgreSQL16/actual Boot JAR', production: false, sourceJarSha256: baseline.sha256, totalMs: Math.round(performance.now() - start), timeline }, null, 2));
