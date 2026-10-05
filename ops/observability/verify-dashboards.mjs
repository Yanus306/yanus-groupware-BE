import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const base = process.env.PROMETHEUS_URL || 'http://localhost:39090';
const directory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'grafana', 'dashboards');
const results = [];
for (const file of fs.readdirSync(directory).filter(name => name.endsWith('.json')).sort()) {
  const board = JSON.parse(fs.readFileSync(path.join(directory, file), 'utf8'));
  for (const panel of board.panels) {
    for (const target of panel.targets || []) {
      if ((target.datasource || panel.datasource)?.type === 'loki') continue;
      const query = target.expr.replaceAll('$environment', 'prod').replaceAll('$instance', '.*')
        .replaceAll('$__rate_interval', '5m').replaceAll('$__range', '30m');
      const url = new URL('/api/v1/query', base);
      url.searchParams.set('query', query);
      const response = await fetch(url, { signal: AbortSignal.timeout(20000) });
      const data = await response.json();
      results.push({ board: board.uid, panel: panel.title, ref: target.refId, status: data.status, series: data.data?.result?.length ?? 0,
        nonfinite: (data.data?.result || []).filter(series => !Number.isFinite(Number(series.value[1]))).length,
        error: data.error || null });
    }
  }
}
console.log(JSON.stringify({ timestamp: new Date().toISOString(), queries: results.length, errors: results.filter(r => r.status !== 'success'), empty: results.filter(r => r.series === 0), nonfinite: results.filter(r => r.nonfinite > 0), results }, null, 2));
if (results.some(result => result.status !== 'success')) process.exitCode = 1;
