import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const execute = promisify(execFile);
await fs.rm('previous-probe-state.json', { force: true });
const repository = process.env.GITHUB_REPOSITORY;
if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? '')) throw new Error('Invalid repository');
const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'yanus-probe-'));
try {
  const { stdout } = await execute('gh', ['run', 'list', '--repo', repository, '--workflow', 'uptime.yml', '--branch', 'main', '--event', 'schedule', '--status', 'completed', '--limit', '1', '--json', 'databaseId'], { timeout: 20000 });
  const [run] = JSON.parse(stdout);
  if (run && Number.isSafeInteger(run.databaseId)) {
    await execute('gh', ['run', 'download', String(run.databaseId), '--repo', repository, '--name', 'probe-state-prod', '--dir', temporary], { timeout: 20000 });
    const file = path.join(temporary, 'probe-state.json');
    if ((await fs.stat(file)).size > 16384) throw new Error('State limit');
    const content = await fs.readFile(file, 'utf8');
    if (!Array.isArray(JSON.parse(content))) throw new Error('State schema');
    await fs.writeFile('previous-probe-state.json', content, { mode: 0o600 });
    console.log('Previous scheduled probe state loaded; freshness checked by probe');
  } else console.log('Previous state UNKNOWN: no completed scheduled run');
} catch {
  // Failure conclusions may have valid artifacts. Missing history is UNKNOWN, never UP.
  console.log('Previous state UNKNOWN: unavailable or invalid artifact');
} finally { await fs.rm(temporary, { recursive: true, force: true }); }
