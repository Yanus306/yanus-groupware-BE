import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const execute = promisify(execFile);
const source = path.join(path.dirname(fileURLToPath(import.meta.url)), 'alertmanager');
const directory = path.resolve('build/alert-routing-qa');
await fs.mkdir(directory, { recursive: true });
const messages = [];
const mock = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    messages.push(JSON.parse(Buffer.concat(chunks).toString('utf8')));
    response.writeHead(200, { 'content-type': 'text/plain' }); response.end('ok');
  });
});
await new Promise(resolve => mock.listen(0, '0.0.0.0', resolve));
const name = `yanus-alert-qa-${process.pid}`;
async function waitFor(condition, milliseconds = 15000) {
  const end = performance.now() + milliseconds;
  while (performance.now() < end) {
    if (await condition()) return;
    await delay(100);
  }
  throw new Error('QA condition timed out');
}
try {
  const config = (await fs.readFile(path.join(source, 'alertmanager.yml'), 'utf8'))
    .replace('group_wait: 30s', 'group_wait: 1s').replace('group_interval: 5m', 'group_interval: 2s');
  await fs.writeFile(path.join(directory, 'alertmanager.yml'), config);
  await fs.copyFile(path.join(source, 'yanus-slack.tmpl'), path.join(directory, 'yanus-slack.tmpl'));
  await fs.writeFile(path.join(directory, 'slack-webhook'), `http://host.docker.internal:${mock.address().port}/mock\n`, { mode: 0o644 });
  const volume = `${directory}:/etc/alertmanager:ro`;
  const checked = await execute('docker', ['run', '--rm', '--entrypoint', 'amtool', '-v', volume, 'prom/alertmanager:v0.28.1', 'check-config', '/etc/alertmanager/alertmanager.yml'], { timeout: 30000 });
  assert.match(checked.stdout, /SUCCESS/);
  await execute('docker', ['run', '-d', '--name', name, '--add-host', 'host.docker.internal:host-gateway', '-p', '127.0.0.1::9093', '-v', volume, 'prom/alertmanager:v0.28.1', '--config.file=/etc/alertmanager/alertmanager.yml', '--web.listen-address=0.0.0.0:9093', '--cluster.listen-address='], { timeout: 30000 });
  const { stdout } = await execute('docker', ['port', name, '9093/tcp']);
  const base = `http://${stdout.trim()}`;
  await waitFor(async () => { try { return (await fetch(`${base}/-/ready`)).ok; } catch { return false; } });
  const startsAt = new Date(Date.now() - 1000).toISOString();
  const labels = { alertname: 'YanusQa', environment: 'prod', service: 'monitoring', instance: 'qa', severity: 'warning' };
  const annotations = { summary: '격리 테스트', description: '실제 업무 장애 아님', dashboard_url: 'https://grafana.yanus.bond/d/yanus-overview', runbook_url: 'https://app.notion.com/p/3f00691a0023813d9b26da18f566b92d' };
  async function post(endsAt, extraLabels = {}) {
    const response = await fetch(`${base}/api/v2/alerts`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify([{ labels: { ...labels, ...extraLabels }, annotations, startsAt, endsAt }]) });
    assert.equal(response.status, 200);
  }
  const future = new Date(Date.now() + 60000).toISOString();
  await post(future); await post(future);
  await waitFor(() => messages.length >= 1);
  assert.equal(messages.length, 1, 'identical alert must group/deduplicate');
  assert.match(messages[0].attachments[0].title, /장애 발생/);
  await post(new Date().toISOString());
  await waitFor(() => messages.some(message => JSON.stringify(message).includes('복구 완료')));
  const prodCount = messages.length;
  await post(future, { environment: 'dev' });
  await delay(2500);
  assert.equal(messages.length, prodCount, 'non-prod must not deliver');
  await post(future, { alertname: 'YanusDiskCritical', severity: 'critical', instance: 'disk-qa' });
  await post(future, { alertname: 'YanusDiskWarning', severity: 'warning', instance: 'disk-qa' });
  await waitFor(() => messages.some(message => JSON.stringify(message).includes('YanusDiskCritical')));
  await delay(2500);
  assert.ok(!messages.some(message => JSON.stringify(message).includes('YanusDiskWarning')), 'same-host critical must inhibit warning');
  assert.ok(JSON.stringify(messages).includes('대응 도움말'));
  await fs.writeFile(path.join(directory, 'evidence.json'), JSON.stringify({ observedAt: new Date().toISOString(), runtime: 'isolated Alertmanager 0.28.1 + real HTTP mock', duplicatePosts: 2, messages, productionSlack: false }, null, 2));
  console.log(JSON.stringify({ status: 'PASS', firingAndResolved: true, deduplication: true, prodOnly: true, diskInhibition: true, evidence: path.join(directory, 'evidence.json') }));
} catch (error) {
  await fs.writeFile(path.join(directory, 'failure.json'), JSON.stringify({ observedAt: new Date().toISOString(), messages, error: error.message }, null, 2));
  throw error;
} finally {
  await execute('docker', ['rm', '-f', name]).catch(() => {});
  mock.closeAllConnections(); await new Promise(resolve => mock.close(resolve));
  await fs.rm(path.join(directory, 'slack-webhook'), { force: true });
}
