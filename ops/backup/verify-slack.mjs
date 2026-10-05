import { spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';

const [mode, receipt] = process.argv.slice(2);
if (!['fire', 'resolve'].includes(mode) || !receipt) throw new Error('사용법: node verify-slack.mjs fire|resolve RECEIPT_FILE');
let alert;
if (mode === 'fire') {
  alert = {
    labels: { alertname: 'YanusBackupConnectionTest', environment: 'prod', service: 'database', instance: 'backup-test', severity: 'info' },
    annotations: {
      summary: '[검증용] DB 백업 실패·복구 알림 전달 시험',
      description: '백업 알림의 Slack 전달과 한국어 도움말을 확인하는 승인된 시험입니다. 실제 DB 장애나 백업 파일 손상이 아닙니다.',
      dashboard_url: 'https://grafana.yanus.bond/d/yanus-postgres',
      runbook_url: 'https://app.notion.com/p/3f00691a0023813d9b26da18f566b92d',
    },
    startsAt: new Date().toISOString(), endsAt: new Date(Date.now() + 20 * 60_000).toISOString(),
  };
  await writeFile(receipt, JSON.stringify(alert), { mode: 0o600, flag: 'wx' });
} else {
  alert = JSON.parse(await readFile(receipt, 'utf8'));
  if (alert.labels?.alertname !== 'YanusBackupConnectionTest') throw new Error('INVALID_TEST_RECEIPT');
  alert.endsAt = new Date().toISOString();
}
const child = spawn('/usr/bin/ssh', ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10',
  'monitoring-server', 'curl -fsS --max-time 10 -H "Content-Type: application/json" --data-binary @- http://127.0.0.1:9093/api/v2/alerts'],
{ stdio: ['pipe', 'ignore', 'ignore'] });
const timer = setTimeout(() => child.kill('SIGKILL'), 30_000);
try {
  await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolve() : reject(new Error('ALERT_INPUT_FAILED')));
    child.stdin.end(JSON.stringify([alert]));
  });
  console.log(JSON.stringify({ status: 'INPUT_ACCEPTED', mode, at: new Date().toISOString(), note: '실제 Slack 수신은 별도 확인해야 합니다.' }));
} finally { clearTimeout(timer); }
