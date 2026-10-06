import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';

const execute = promisify(execFile);
const HELPER = '/usr/local/libexec/yanus-backup/transport.sh';

export function sshTransport(host) {
  if (!/^[a-z][a-z0-9-]{0,62}$/.test(host)) throw new Error('INVALID_SSH_ALIAS');
  const args = ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10',
    '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=2', host, HELPER];
  return {
    async list() {
      const result = await execute('/usr/bin/ssh', [...args, 'list'], { timeout: 60_000, maxBuffer: 1024 * 1024 });
      return result.stdout;
    },
    async archive(id, destination) {
      const child = spawn('/usr/bin/ssh', [...args, 'archive', id], { stdio: ['ignore', 'pipe', 'ignore'] });
      const exit = new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('exit', (code, signal) => code === 0 ? resolve() : reject(new Error(`TRANSFER_FAILED:${code ?? signal}`)));
      });
      const timer = setTimeout(() => child.kill('SIGKILL'), 120_000);
      try {
        await Promise.all([pipeline(child.stdout, createWriteStream(destination, { flags: 'wx', mode: 0o600 })), exit]);
      } finally {
        clearTimeout(timer);
        if (child.exitCode === null) child.kill('SIGKILL');
      }
    },
    async ack(metadata) {
      await execute('/usr/bin/ssh', [...args, 'ack', metadata.id, metadata.sha256], { timeout: 60_000 });
    },
  };
}
