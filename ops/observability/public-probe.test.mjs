import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { probe, nextState, notificationFor, sendSlack, TARGETS } from './public-probe.mjs';

async function server(t, handler) {
  const s = http.createServer(handler);
  await new Promise(resolve => s.listen(0, '127.0.0.1', resolve));
  t.after(() => { s.closeAllConnections(); s.close(); });
  return `http://127.0.0.1:${s.address().port}`;
}
test('real HTTP JSON contract succeeds, HTML 200 and redirect fail', async t => {
  for (const [status, type, body, expected] of [
    [200, 'application/json', JSON.stringify({ openapi: '3.1.0', info: { title: 'yANUs groupware API' } }), 'UP'],
    [200, 'text/html', '<html>Cloudflare</html>', 'DOWN'],
    [302, 'text/html', 'redirect', 'DOWN'],
    [503, 'application/json', '{}', 'DOWN'],
    [200, 'application/json', '{}', 'DOWN'],
  ]) {
    const url = await server(t, (_, res) => { res.writeHead(status, { 'content-type': type }); res.end(body); });
    assert.equal((await probe({ ...TARGETS[0], url }, { attempts: 1 })).status, expected);
  }
});
test('timeout and oversized/malformed body are bounded failures without body leaks', async t => {
  const stalled = await server(t, () => {});
  assert.equal((await probe({ ...TARGETS[0], url: stalled }, { timeoutMs: 30, attempts: 1 })).reason, 'TIMEOUT');
  const big = await server(t, (_, r) => { r.writeHead(200, { 'content-type': 'application/json' }); r.end('secret-canary'.repeat(100)); });
  const result = await probe({ ...TARGETS[0], url: big }, { maxBytes: 100, attempts: 1 });
  assert.equal(result.reason, 'BODY_LIMIT');
  assert.ok(!JSON.stringify(result).includes('secret-canary'));
});
test('Grafana database and retry contracts', async t => {
  let count = 0;
  const url = await server(t, (_, r) => {
    r.writeHead(++count === 1 ? 503 : 200, { 'content-type': 'application/json' });
    r.end(JSON.stringify({ database: 'ok' }));
  });
  assert.equal((await probe({ ...TARGETS[1], url }, { retryMs: 1 })).status, 'UP');
  assert.equal(count, 2);
});
const when = new Date('2026-10-05T00:00:00Z');
const down = { id: 'api', status: 'DOWN', reason: 'TIMEOUT' };
const up = { id: 'api', status: 'UP', reason: 'OK' };
test('malformed or untrusted webhook cannot crash a healthy observation or leak secret', async () => {
  for (const webhook of ['', 'secret-canary', 'https://example.com/services/secret-canary']) {
    const result = await sendSlack(webhook, down, 'DOWN', when);
    assert.ok(['missing-secret', 'invalid-secret'].includes(result));
  }
});
test('Slack delivery requires HTTP 200 and ok; rejection/redirect/body limits remain failures', async t => {
  for (const [status, body, expected] of [[200, 'ok', 'sent'], [200, 'rejected', 'failed'], [429, 'rate_limited', 'failed'], [500, 'error', 'failed'], [302, 'ok', 'failed'], [200, 'x'.repeat(1025), 'failed']]) {
    const url = await server(t, (req, res) => {
      let payload = '';
      req.on('data', chunk => { payload += chunk; });
      req.on('end', () => {
        assert.ok(!payload.includes('secret-canary'));
        assert.match(JSON.parse(payload).text, /대응:/);
        res.writeHead(status, { 'content-type': 'text/plain', ...(status === 302 ? { location: url } : {}) }); res.end(body);
      });
    });
    const forwarded = (_, options) => fetch(url, options);
    assert.equal(await sendSlack('https://hooks.slack.com/services/secret-canary', down, 'DOWN', when, forwarded), expected);
  }
});
test('initial failure, delivered recovery and hourly reminder; no invented recovery', () => {
  assert.equal(notificationFor(up, null, when), null);
  assert.equal(notificationFor(down, null, when), 'DOWN');
  const failedDelivery = nextState(down, null, when, 'failed');
  assert.equal(notificationFor(up, failedDelivery, new Date(+when + 1000)), null);
  assert.equal(notificationFor(down, failedDelivery, new Date(+when + 1000)), 'DOWN');
  const delivered = nextState(down, null, when, 'sent');
  assert.equal(notificationFor(up, delivered, new Date(+when + 1000)), 'RECOVERED');
  assert.equal(notificationFor(down, delivered, new Date(+when + 1000)), null);
  const recentCheck = { ...delivered, checkedAt: new Date(+when + 3599000).toISOString() };
  assert.equal(notificationFor(down, recentCheck, new Date(+when + 3600000)), 'REMINDER');
});
test('expired, future, malformed and changed-contract state cannot manufacture recovery', () => {
  const old = nextState(down, null, when, 'sent');
  for (const previous of [
    { ...old, checkedAt: 'bad' },
    { ...old, checkedAt: new Date(+when + 60000).toISOString() },
    { ...old, checkedAt: new Date(+when - 1800001).toISOString() },
    { ...old, contract: 'another' },
    { ...old, lastNotifiedStatus: 'invented' },
  ]) assert.equal(notificationFor(up, previous, when), null);
});
