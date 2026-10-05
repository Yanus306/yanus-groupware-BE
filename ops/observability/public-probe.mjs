import fs from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { createHash } from 'node:crypto';

export const TARGETS = [
  { id: 'api', url: 'https://api.yanus.bond/v3/api-docs', kind: 'openapi' },
  { id: 'grafana', url: 'https://grafana.yanus.bond/api/health', kind: 'grafana' },
];
export const CONTRACT = createHash('sha256').update(JSON.stringify({ version: 1, targets: TARGETS, apiTitle: 'yANUs groupware API' })).digest('hex');
const MAX_AGE = 30 * 60 * 1000;
const REMINDER = 60 * 60 * 1000;
const REASONS = new Set(['OK', 'TIMEOUT', 'DNS', 'TLS', 'CONNECT', 'HTTP_STATUS', 'CONTENT_TYPE', 'BODY_LIMIT', 'JSON_CONTRACT']);

async function readBounded(response, maxBytes) {
  let size = 0;
  const parts = [];
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > maxBytes) throw Object.assign(new Error('BODY_LIMIT'), { code: 'BODY_LIMIT' });
    parts.push(chunk);
  }
  return Buffer.concat(parts).toString('utf8');
}
function networkReason(error) {
  if (error.name === 'TimeoutError' || error.name === 'AbortError') return 'TIMEOUT';
  const code = error.cause?.code ?? error.code ?? '';
  if (code === 'BODY_LIMIT') return code;
  if (code === 'ENOTFOUND' || code === 'EAI_AGAIN') return 'DNS';
  if (/CERT|TLS|SSL|SELF_SIGNED/.test(code)) return 'TLS';
  return 'CONNECT';
}
export async function probe(target, { timeoutMs = 10000, maxBytes = 1048576, attempts = 2, retryMs = 1000 } = {}) {
  const start = performance.now();
  let reason = 'CONNECT';
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      const response = await fetch(target.url, { redirect: 'manual', signal: AbortSignal.timeout(timeoutMs), headers: { accept: 'application/json', 'cache-control': 'no-cache' } });
      if (response.status !== 200) { reason = 'HTTP_STATUS'; await response.body?.cancel(); }
      else if (!/^application\/(?:[\w.+-]+\+)?json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '')) {
        reason = 'CONTENT_TYPE'; await response.body?.cancel();
      } else {
        const text = await readBounded(response, maxBytes);
        let data;
        try { data = JSON.parse(text); } catch { reason = 'JSON_CONTRACT'; }
        const valid = target.kind === 'openapi'
          ? typeof data?.openapi === 'string' && /^3\./.test(data.openapi) && data?.info?.title === 'yANUs groupware API'
          : target.kind === 'grafana' && data?.database === 'ok';
        if (valid) return { id: target.id, status: 'UP', reason: 'OK', elapsedMs: Math.round(performance.now() - start) };
        reason = 'JSON_CONTRACT';
      }
    } catch (error) { reason = networkReason(error); }
    if (attempt + 1 < attempts) await delay(retryMs);
  }
  return { id: target.id, status: 'DOWN', reason, elapsedMs: Math.round(performance.now() - start) };
}
function fresh(previous, now) {
  if (!previous || previous.contract !== CONTRACT || !['UP', 'DOWN'].includes(previous.status)) return null;
  if (![null, 'UP', 'DOWN'].includes(previous.lastNotifiedStatus)) return null;
  if (!REASONS.has(previous.reason)) return null;
  const age = +now - Date.parse(previous.checkedAt);
  if (!Number.isFinite(age) || age < 0 || age > MAX_AGE) return null;
  if (previous.lastNotifiedStatus !== null) {
    const timestamp = Date.parse(previous.lastNotifiedAt);
    if (!Number.isFinite(timestamp) || timestamp > Date.parse(previous.checkedAt)) return null;
  }
  return previous;
}
export function notificationFor(result, previous, now = new Date()) {
  const p = fresh(previous, now);
  if (result.status === 'UP') return p?.lastNotifiedStatus === 'DOWN' ? 'RECOVERED' : null;
  if (result.status !== 'DOWN') return null;
  if (p?.lastNotifiedStatus !== 'DOWN') return 'DOWN';
  return +now - Date.parse(p.lastNotifiedAt) >= REMINDER ? 'REMINDER' : null;
}
export function nextState(result, previous, now, delivery) {
  const p = fresh(previous, now);
  return {
    ...result, contract: CONTRACT, checkedAt: now.toISOString(), delivery,
    lastNotifiedStatus: delivery === 'sent' ? result.status : p?.lastNotifiedStatus ?? null,
    lastNotifiedAt: delivery === 'sent' ? now.toISOString() : p?.lastNotifiedAt ?? null,
  };
}
export async function sendSlack(webhook, result, kind, now, request = fetch) {
  if (!webhook) return 'missing-secret';
  let url;
  try { url = new URL(webhook); } catch { return 'invalid-secret'; }
  if (url.protocol !== 'https:' || url.hostname !== 'hooks.slack.com' || url.username || url.password || url.search || url.hash || !url.pathname.startsWith('/services/')) return 'invalid-secret';
  const message = {
    text: `[yANUs PROD] ${kind} · ${result.id} · ${result.reason}\n확인: ${now.toISOString()}\nGrafana: https://grafana.yanus.bond/\n대응: https://app.notion.com/p/3f00691a0023813d9b26da18f566b92d`,
  };
  try {
    const response = await request(webhook, { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(10000), headers: { 'content-type': 'application/json' }, body: JSON.stringify(message) });
    const body = await readBounded(response, 1024);
    return response.status === 200 && body.trim() === 'ok' ? 'sent' : 'failed';
  } catch { return 'failed'; }
}
export async function run({ previous = [], dryRun = false, webhook = '', targets = TARGETS } = {}) {
  const results = await Promise.all(targets.map(target => probe(target)));
  const now = new Date();
  const state = [];
  for (const result of results) {
    const p = Array.isArray(previous) ? previous.find(item => item?.id === result.id) : null;
    const kind = notificationFor(result, p, now);
    const delivery = kind && !dryRun ? await sendSlack(webhook, result, kind, now) : dryRun && kind ? 'dry-run' : 'not-needed';
    state.push(nextState(result, p, now, delivery));
  }
  return state;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const dryRun = process.argv.includes('--dry-run');
  let previous = [];
  try { previous = JSON.parse(await fs.readFile('previous-probe-state.json', 'utf8')); } catch {}
  const state = await run({ previous, dryRun, webhook: process.env.SLACK_WEBHOOK_URL ?? '' });
  await fs.writeFile('probe-state.json', `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
  console.log(JSON.stringify(state));
  if (state.some(item => item.status !== 'UP' || ['failed', 'missing-secret', 'invalid-secret'].includes(item.delivery))) process.exitCode = 1;
}
