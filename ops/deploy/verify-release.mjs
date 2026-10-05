import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { probe } from '../observability/public-probe.mjs';

async function json(url, timeoutMs) {
  const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs), redirect: 'error' });
  if (response.status !== 200 || !/^application\/(?:[\w.+-]+\+)?json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '')) throw new Error('HTTP_CONTRACT');
  let size = 0; const chunks = [];
  for await (const chunk of response.body) { size += chunk.length; if (size > 65536) throw new Error('BODY_LIMIT'); chunks.push(chunk); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
export async function verifyOnce({ management, main, commit, legacy = false, timeoutMs = 3000 }) {
  try {
    const health = await json(`${management}/actuator/health${legacy ? '' : '/readiness'}`, timeoutMs);
    if (health.status !== 'UP') return { ok: false, reason: 'READINESS' };
    if (!legacy) {
      const info = await json(`${management}/actuator/info`, timeoutMs);
      if (info.build?.commit !== commit) return { ok: false, reason: 'BUILD_IDENTITY' };
    }
    const api = await probe({ id: 'main', kind: 'openapi', url: `${main}/v3/api-docs` }, { timeoutMs, attempts: 1 });
    return { ok: api.status === 'UP', reason: api.reason };
  } catch { return { ok: false, reason: 'MANAGEMENT_HTTP' }; }
}
export async function verifyRelease(options, { deadlineMs = 120000, intervalMs = 1000 } = {}) {
  const end = performance.now() + deadlineMs;
  let result = { ok: false, reason: 'DEADLINE' };
  while (performance.now() < end) {
    result = await verifyOnce({ ...options, timeoutMs: Math.max(1, Math.min(3000, Math.floor((end - performance.now()) / 3))) });
    if (result.ok) return result;
    await delay(Math.max(0, Math.min(intervalMs, end - performance.now())));
  }
  return result;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [commit, mode = 'candidate', management = 'http://127.0.0.1:9091', main = 'http://127.0.0.1:8080'] = process.argv.slice(2);
  if (mode !== 'legacy' && !/^[a-f0-9]{40}$/.test(commit ?? '')) throw new Error('EXPECTED_COMMIT_REQUIRED');
  const result = await verifyRelease({ management, main, commit, legacy: mode === 'legacy' });
  console.log(JSON.stringify(result)); if (!result.ok) process.exitCode = 1;
}
