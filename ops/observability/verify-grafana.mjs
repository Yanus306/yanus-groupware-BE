import fs from 'node:fs/promises';

const base = process.env.GRAFANA_URL || 'http://127.0.0.1:3300';
const passwordFile = process.env.GRAFANA_PASSWORD_FILE;
if (!passwordFile) throw new Error('GRAFANA_PASSWORD_FILE is required');
const password = (await fs.readFile(passwordFile, 'utf8')).trim();
const username = process.env.GRAFANA_USERNAME || 'yanus';
const headers = { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}` };
async function get(path) {
  const response = await fetch(`${base}${path}`, { headers, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}
const expected = ['yanus-overview', 'yanus-api', 'yanus-logs', 'yanus-hosts', 'yanus-postgres', 'yanus-traces'];
const results = [];
for (const uid of expected) {
  const { dashboard, meta } = await get(`/api/dashboards/uid/${uid}`);
  if (meta.folderUid !== 'yanus-prod' || !meta.provisioned || dashboard.links.length !== 6) {
    throw new Error(`Invalid provisioning: ${uid}`);
  }
  const helpIndex = dashboard.panels.findIndex(panel => panel.id === 10000 && panel.type === 'row');
  const guidance = dashboard.panels[helpIndex]?.panels?.find(panel => panel.type === 'text');
  const separator = dashboard.panels[helpIndex + 1];
  if ((uid !== 'yanus-traces' && (!guidance?.options?.content || separator?.type !== 'row' || separator.collapsed))
      || (uid === 'yanus-traces' && !dashboard.panels.some(panel => panel.type === 'text' && panel.options?.content))
      || dashboard.panels.some(panel => panel.targets?.length && !panel.description)) {
    throw new Error(`Missing guidance or hidden metrics: ${uid}`);
  }
  results.push({ uid, title: dashboard.title, panels: dashboard.panels.length, provisioned: meta.provisioned, help: true });
  if (uid === 'yanus-logs' && (!dashboard.templating.list.some(variable => variable.name === 'requestId')
      || !dashboard.panels.some(panel => panel.type === 'logs' && panel.datasource?.uid === 'yanus-loki'))) {
    throw new Error('Missing native Loki request tracing');
  }
}
const health = await get('/api/datasources/uid/yanus-prometheus/health');
if (health.status !== 'OK') throw new Error(`Datasource: ${health.status}`);
const lokiHealth = await get('/api/datasources/uid/yanus-loki/health');
if (lokiHealth.status !== 'OK') throw new Error(`Loki datasource: ${lokiHealth.status}`);
const tempoHealth = await get('/api/datasources/uid/yanus-tempo/health');
if (tempoHealth.status !== 'OK') throw new Error(`Tempo datasource: ${tempoHealth.status}`);
const loki = await get('/api/datasources/uid/yanus-loki');
const derivedTrace = loki.jsonData?.derivedFields?.find(field => field.name === 'TraceID');
if (derivedTrace?.datasourceUid !== 'yanus-tempo' || derivedTrace.url !== '${__value.raw}') {
  throw new Error('Loki trace link macro lost during provisioning');
}
const tempo = await get('/api/datasources/uid/yanus-tempo');
if (!tempo.jsonData?.tracesToLogsV2?.query?.includes('${__trace.traceId}')) {
  throw new Error('Tempo log link macro lost during provisioning');
}
const { dashboard: traceBoard } = await get('/api/dashboards/uid/yanus-traces');
const search = traceBoard.panels.find(panel => panel.datasource?.uid === 'yanus-tempo').targets[0];
const response = await fetch(`${base}/api/ds/query`, {
  method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(15000),
  body: JSON.stringify({ queries: [{ ...search, query: search.query.replace('$minDuration', '100ms'),
    intervalMs: 15000, maxDataPoints: 100 }], from: String(Date.now() - 1800000), to: String(Date.now()) }),
});
if (!response.ok) throw new Error(`Tempo query: HTTP ${response.status}`);
const result = (await response.json()).results?.A;
if (result?.error || result?.status !== 200
    || !result.frames?.some(frame => frame.schema.fields.some(field => field.name === 'traceID'))) {
  throw new Error('Tempo dashboard query failed');
}
console.log(JSON.stringify({ dashboards: results, datasource: health.status, loki: lokiHealth.status,
  tempo: tempoHealth.status, traceQuery: 'OK' }, null, 2));
