import { probe, TARGETS } from '../observability/public-probe.mjs';
import { setTimeout as delay } from 'node:timers/promises';
import fs from 'node:fs/promises';

const baseline = JSON.parse(await fs.readFile('/etc/yanus-deploy/baseline.json', 'utf8'));
const target = baseline.environment === 'qa' ? { ...TARGETS[0], url: baseline.qaPublicUrl } : TARGETS[0];

const end = performance.now() + 60000;
let result;
while (performance.now() < end) {
  result = await probe(target, { attempts: 1, timeoutMs: Math.max(1, Math.min(10000, Math.floor(end - performance.now()))) });
  if (result.status === 'UP') break;
  await delay(Math.max(0, Math.min(1000, end - performance.now())));
}
console.log(JSON.stringify({ stage: 'PUBLIC_HTTP', ...result }));
if (result?.status !== 'UP') process.exitCode = 1;
