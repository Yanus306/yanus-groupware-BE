import { spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';

const [mode, receipt, category = 'backup', extra] = process.argv.slice(2);
if (!['fire', 'resolve'].includes(mode) || !receipt || !['backup', 'restore'].includes(category) || extra) throw new Error('사용법: node verify-slack.mjs fire|resolve RECEIPT_FILE [backup|restore]');
const restoring = category === 'restore';
const alertname = restoring ? 'YanusRestoreConnectionTest' : 'YanusBackupConnectionTest';
let alert;
if (mode === 'fire') {
  alert = {
    labels: { alertname, environment: 'prod', service: 'database', instance: restoring ? 'restore-test' : 'backup-test', severity: 'info' },
    annotations: {
      summary: restoring ? '[검증용] 격리 DB 복원 실패·재검증 알림 전달 시험' : '[검증용] DB 백업 실패·복구 알림 전달 시험',
      description: restoring ? '격리 훈련의 잘못된 키·손상 dump 거절과 후속 실제 복원 성공을 확인한 뒤 수행하는 Slack 전달 시험입니다. 실제 운영 DB 장애가 아닙니다.' : '백업 알림의 Slack 전달과 한국어 도움말을 확인하는 승인된 시험입니다. 실제 DB 장애나 백업 파일 손상이 아닙니다.',
      dashboard_url: 'https://grafana.yanus.bond/d/yanus-postgres',
      runbook_url: 'https://app.notion.com/p/3f00691a002381609c49cfba79622c3a',
    },
    startsAt: new Date().toISOString(), endsAt: new Date(Date.now() + 20 * 60_000).toISOString(),
  };
  await writeFile(receipt, JSON.stringify(alert), { mode: 0o600, flag: 'wx' });
} else {
  alert = JSON.parse(await readFile(receipt, 'utf8'));
  if (alert.labels?.alertname !== alertname) throw new Error('INVALID_TEST_RECEIPT');
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
