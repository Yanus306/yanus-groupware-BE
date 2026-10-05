import fs from 'node:fs/promises';
import { sendSlack } from '../observability/public-probe.mjs';

const [status, commit] = process.argv.slice(2);
if (!['SUCCESS', 'ROLLED_BACK', 'ROLLBACK_FAILED'].includes(status) || !/^[a-f0-9]{40}$/.test(commit ?? '')) throw new Error('Invalid deployment event');
let webhook = '';
try { webhook = (await fs.readFile('/etc/yanus-deploy/slack-webhook', 'utf8')).trim(); } catch {}
const delivery = await sendSlack(webhook, { id: 'deployment', reason: `${status} ${commit}` }, status, new Date());
console.log(JSON.stringify({ stage: 'NOTIFICATION', status, commit, delivery }));
