import fs from 'node:fs/promises';

const base = process.env.GRAFANA_URL || 'http://127.0.0.1:3300';
const passwordFile = process.env.GRAFANA_PASSWORD_FILE;
if (!passwordFile) throw new Error('GRAFANA_PASSWORD_FILE is required');
const password = (await fs.readFile(passwordFile, 'utf8')).trim();
const username = process.env.GRAFANA_USERNAME || 'yanus';
const headers = { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}` };
async function get(path) {
  const response = await fetch(`${base}${path}`, { headers });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}
const expected = ['yanus-overview', 'yanus-api', 'yanus-logs', 'yanus-hosts', 'yanus-postgres'];
const results = [];
for (const uid of expected) {
  const { dashboard, meta } = await get(`/api/dashboards/uid/${uid}`);
  if (meta.folderUid !== 'yanus-prod' || !meta.provisioned || dashboard.links.length !== 5) {
    throw new Error(`Invalid provisioning: ${uid}`);
  }
  results.push({ uid, title: dashboard.title, panels: dashboard.panels.length, provisioned: meta.provisioned });
}
const health = await get('/api/datasources/uid/yanus-prometheus/health');
if (health.status !== 'OK') throw new Error(`Datasource: ${health.status}`);
console.log(JSON.stringify({ dashboards: results, datasource: health.status }, null, 2));
