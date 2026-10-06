import { spawn } from 'node:child_process';
import { open } from 'node:fs/promises';
import { constants } from 'node:fs';

export async function acquireLock(path) {
  const file = await open(path, constants.O_CREAT | constants.O_RDWR | constants.O_NOFOLLOW, 0o600);
  await file.close();
  const command = process.platform === 'darwin' ? '/usr/bin/lockf' : 'flock';
  const flags = process.platform === 'darwin' ? ['-s', '-t', '0', '-k'] : ['-n'];
  const child = spawn(command, [...flags, path, 'sh', '-c', 'printf LOCKED; cat >/dev/null'], {
    stdio: ['pipe', 'pipe', 'ignore'],
  });
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('LOCK_TIMEOUT')), 5000);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('exit', () => { clearTimeout(timer); reject(Object.assign(new Error('COLLECTION_BUSY'), { code: 'EEXIST' })); });
      child.stdout.once('data', () => { clearTimeout(timer); resolve(); });
    });
  } catch (error) { child.kill(); throw error; }
  return async () => {
    await new Promise(resolve => { child.once('exit', resolve); child.stdin.end(); });
  };
}
