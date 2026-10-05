import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { probe, TARGETS } from './public-probe.mjs';

test('actual untrusted TLS certificate is rejected without disabling validation', async t => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'yanus-tls-test-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  await promisify(execFile)('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', path.join(dir, 'key.pem'), '-out', path.join(dir, 'cert.pem'), '-days', '1', '-subj', '/CN=localhost'], { timeout: 10000 });
  const server = https.createServer({ key: await fs.readFile(path.join(dir, 'key.pem')), cert: await fs.readFile(path.join(dir, 'cert.pem')) }, (_, response) => response.end('{}'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const result = await probe({ ...TARGETS[0], url: `https://127.0.0.1:${server.address().port}` }, { attempts: 1, timeoutMs: 1000 });
  assert.equal(result.status, 'DOWN'); assert.equal(result.reason, 'TLS');
});
