import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { verifyOnce, verifyRelease } from './verify-release.mjs';
import { checkCompatibility } from './preflight.mjs';
import { digest, migrationChecksum } from './jar-metadata.mjs';

test('actual management and main HTTP both required; wrong commit/DB/HTML cannot succeed', async t => {
  let healthy = true; let commit = 'a'.repeat(40); let html = false;
  const server = http.createServer((req, res) => {
    res.writeHead(healthy || !req.url.includes('readiness') ? 200 : 503, { 'content-type': html && req.url === '/v3/api-docs' ? 'text/html' : 'application/vnd.spring-boot.actuator.v3+json' });
    res.end(JSON.stringify(req.url.includes('health') ? { status: healthy ? 'UP' : 'DOWN' } : req.url.includes('info') ? { build: { commit } } : { openapi: '3.1.0', info: { title: 'yANUs groupware API' } }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const address = `http://127.0.0.1:${server.address().port}`;
  const options = { management: address, main: address, commit };
  assert.equal((await verifyOnce(options)).ok, true);
  commit = 'b'.repeat(40); assert.equal((await verifyOnce(options)).reason, 'BUILD_IDENTITY');
  commit = options.commit; healthy = false; assert.equal((await verifyOnce(options)).ok, false);
  healthy = true; html = true; assert.equal((await verifyOnce(options)).ok, false);
  assert.equal((await verifyRelease(options, { deadlineMs: 50, intervalMs: 5 })).ok, false);
  html = false; assert.equal((await verifyOnce({ ...options, main: 'http://127.0.0.1:1' })).ok, false);
});
test('checksum/source/schema/history gates reject before activation', () => {
  const migrations = [{ version: '1', script: 'V1__test.sql', checksum: 42, sha256: 'sql' }];
  const candidate = { sha256: 'a'.repeat(64), size: 100, commit: 'a'.repeat(40), migrations };
  const history = [{ version: '1', script: 'V1__test.sql', checksum: 42, type: 'SQL', success: true }];
  const baseline = { sha256: candidate.sha256, sourceCommit: candidate.commit, schemaSha256: digest(JSON.stringify(history)), compatibilityApproved: true };
  const manifest = { version: 1, ...candidate, observationMode: 'native', schemaPolicy: 'unchanged' };
  checkCompatibility(manifest, candidate, baseline, candidate, history);
  for (const altered of [{ ...manifest, sha256: 'wrong' }, { ...manifest, commit: 'b'.repeat(40) }, { ...manifest, schemaPolicy: 'guess-additive' }]) {
    assert.throws(() => checkCompatibility(altered, candidate, baseline, candidate, history));
  }
  assert.throws(() => checkCompatibility(manifest, candidate, { ...baseline, sourceCommit: null }, candidate, history));
  assert.throws(() => checkCompatibility(manifest, candidate, baseline, { ...candidate, migrations: [] }, history));
  assert.throws(() => checkCompatibility(manifest, candidate, baseline, candidate, [{ ...history[0], checksum: 0 }]));
});
test('Flyway checksum is signed CRC32 with BOM and line ending normalization', () => {
  assert.equal(migrationChecksum('123456789'), -873187034);
  assert.equal(migrationChecksum('\uFEFFselect\r\n1;'), migrationChecksum('select\n1;'));
});
