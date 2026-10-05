import fs from 'node:fs/promises';

const base = process.env.GRAFANA_URL || 'http://127.0.0.1:3310';
const passwordFile = process.env.GRAFANA_PASSWORD_FILE;
if (!passwordFile) throw new Error('GRAFANA_PASSWORD_FILE is required');
const password = (await fs.readFile(passwordFile, 'utf8')).trim();
const username = process.env.GRAFANA_USERNAME || 'yanus';
const requestId = process.env.REQUEST_ID || '';
if (requestId && !/^(?:[a-fA-F0-9]{32}|[a-fA-F0-9-]{36})$/.test(requestId)) {
  throw new Error('Request ID must be hexadecimal or UUID');
}
const board = JSON.parse(await fs.readFile(new URL('./grafana/dashboards/03-logs.json', import.meta.url)));
const panel = board.panels.find(item => item.type === 'logs');
const query = panel.targets[0].expr.replaceAll('$environment', 'prod')
  .replaceAll('${requestId:doublequote}', JSON.stringify(requestId));
const url = new URL('/api/datasources/proxy/uid/yanus-loki/loki/api/v1/query_range', base);
url.searchParams.set('query', query);
url.searchParams.set('start', String((Date.now() - 30 * 60 * 1000) * 1e6));
url.searchParams.set('end', String(Date.now() * 1e6));
url.searchParams.set('limit', '200');
const started = performance.now();
const response = await fetch(url, {
  headers: { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}` },
  signal: AbortSignal.timeout(20000),
});
if (!response.ok) throw new Error(`Loki proxy: HTTP ${response.status}`);
const result = await response.json();
if (result.status !== 'success') throw new Error('Loki query failed');
const entries = result.data.result.flatMap(stream => stream.values.map(([, line]) => JSON.parse(line)));
if (!entries.length) throw new Error('No application logs received');
if (requestId && entries.some(entry => entry.requestId !== requestId)) {
  throw new Error('Request ID filter returned unrelated logs');
}
console.log(JSON.stringify({ status: result.status, lines: entries.length,
  requestId: requestId || null, elapsedMs: Math.round(performance.now() - started),
  errors: entries.filter(entry => entry.level === 'ERROR').length,
  exceptionFrames: entries.some(entry => entry.exceptionType && entry.stackTrace),
  events: [...new Set(entries.map(entry => entry.event).filter(Boolean))],
}, null, 2));
