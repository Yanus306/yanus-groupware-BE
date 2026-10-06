import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { chmod, lstat, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const executeFile = promisify(execFile);
const controller = new AbortController();
const execute = (file, args, options) => executeFile(file, args, { ...options, signal: controller.signal });
let activeChild;
for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => { controller.abort(); activeChild?.kill('SIGTERM'); });
const [action, root, host = 'yanus-hyperv', archive] = process.argv.slice(2);
const directory = dirname(fileURLToPath(import.meta.url));
let temporary;
async function remote(mode, input) {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9.-]*$/.test(host)) throw new Error('INVALID_SSH_ALIAS');
  const script = `$Action = '${mode}'\n${await readFile(join(directory, 'windows-key-recovery.ps1'), 'utf8')}`;
  const compressed = gzipSync(Buffer.from(script)).toString('base64');
  const wrapper = `$m=New-Object IO.MemoryStream(,[Convert]::FromBase64String('${compressed}'));$g=New-Object IO.Compression.GZipStream($m,[IO.Compression.CompressionMode]::Decompress);$r=New-Object IO.StreamReader($g);Invoke-Expression $r.ReadToEnd()`;
  const command = `powershell -NoProfile -NonInteractive -EncodedCommand ${Buffer.from(wrapper, 'utf16le').toString('base64')}`;
  const { spawn } = await import('node:child_process');
  return new Promise((resolve, reject) => {
    if (controller.signal.aborted) return reject(new Error('INTERRUPTED'));
    const child = spawn('ssh', ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', host, command], { stdio: ['pipe', 'pipe', 'pipe'] });
    activeChild = child;
    let output = Buffer.alloc(0);
    let failed = false;
    const timer = setTimeout(() => { failed = true; child.kill('SIGTERM'); }, 30000);
    child.stdout.on('data', data => { output = Buffer.concat([output, data]); if (output.length > 8192) { failed = true; child.kill('SIGTERM'); } });
    child.stderr.resume();
    child.on('error', () => { clearTimeout(timer); reject(new Error('SSH_FAILED')); });
    child.on('close', code => { clearTimeout(timer); activeChild = undefined; if (code !== 0 || failed || controller.signal.aborted) { output.fill(0); reject(new Error('REMOTE_KEY_OPERATION_FAILED')); } else resolve(output); });
    child.stdin.on('error', () => {});
    child.stdin.end(input);
  });
}
try {
  if (!root || !['store', 'verify', 'recover'].includes(action)) throw new Error('USAGE_STORE_VERIFY_RECOVER_ROOT_HOST_ARCHIVE');
  const identity = join(root, 'keys', 'identity.txt');
  const recipientFile = join(root, 'recovery-recipient.txt');
  if (action === 'store') {
    const stat = await lstat(identity);
    if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0 || stat.size > 4096) throw new Error('UNSAFE_IDENTITY');
    const { stdout } = await execute('age-keygen', ['-y', identity], { timeout: 10000 });
    const recipient = stdout.trim();
    if (!/^age1[0-9a-z]{58}$/.test(recipient)) throw new Error('INVALID_RECIPIENT');
    const key = await readFile(identity);
    try {
      const receipt = JSON.parse((await remote('store', key.toString('base64'))).toString('utf8'));
      if (receipt.status !== 'STORED' || receipt.protection !== 'DPAPI_CURRENT_USER') throw new Error('INVALID_RECEIPT');
      await writeFile(recipientFile, `${recipient}\n`, { mode: 0o600 });
      await chmod(recipientFile, 0o600);
      console.log(JSON.stringify(receipt));
    } finally { key.fill(0); }
  } else {
    const recipient = (await readFile(recipientFile, 'utf8')).trim();
    if (!/^age1[0-9a-z]{58}$/.test(recipient)) throw new Error('INVALID_RECIPIENT');
    temporary = await mkdtemp(join(tmpdir(), 'yanus-key-recovery-'));
    await chmod(temporary, 0o700);
    const encoded = await remote('recover');
    const key = Buffer.from(encoded.toString('ascii').trim(), 'base64');
    encoded.fill(0);
    const recovered = join(temporary, 'identity.txt');
    try { await writeFile(recovered, key, { mode: 0o600, flag: 'wx' }); } finally { key.fill(0); }
    const { stdout } = await execute('age-keygen', ['-y', recovered], { timeout: 10000 });
    if (stdout.trim() !== recipient) throw new Error('RECIPIENT_MISMATCH');
    if (action === 'verify') {
      if (!archive) throw new Error('ARCHIVE_REQUIRED');
      const { spawn } = await import('node:child_process');
      await new Promise((resolve, reject) => {
        const child = spawn('age', ['--decrypt', '--identity', recovered, archive], { stdio: ['ignore', 'ignore', 'ignore'] });
        activeChild = child;
        const timer = setTimeout(() => child.kill('SIGTERM'), 60000);
        child.on('error', () => { clearTimeout(timer); reject(new Error('AGE_FAILED')); });
        child.on('close', code => { clearTimeout(timer); activeChild = undefined; code === 0 && !controller.signal.aborted ? resolve() : reject(new Error('DECRYPTION_FAILED')); });
      });
      console.log(JSON.stringify({ status: 'VERIFIED', recipient_matches: true, actual_archive_decrypted: true }));
    } else {
      await mkdir(join(root, 'keys'), { recursive: true, mode: 0o700 });
      const keyDirectory = await lstat(join(root, 'keys'));
      if (!keyDirectory.isDirectory() || keyDirectory.isSymbolicLink() || (keyDirectory.mode & 0o077) !== 0) throw new Error('UNSAFE_KEY_DIRECTORY');
      const key = await readFile(recovered);
      try { await writeFile(identity, key, { mode: 0o600, flag: 'wx' }); } finally { key.fill(0); }
      console.log(JSON.stringify({ status: 'RECOVERED', recipient_matches: true }));
    }
  }
} catch (error) {
  console.error(JSON.stringify({ status: 'FAILED', code: error.message?.match(/^[A-Z_]+$/)?.[0] || 'KEY_RECOVERY_FAILED' }));
  process.exitCode = 1;
} finally { if (temporary) await rm(temporary, { recursive: true, force: true }); }
