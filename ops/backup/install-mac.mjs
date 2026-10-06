import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { chmod, copyFile, mkdir, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'darwin') throw new Error('MAC_REQUIRED');
const execute = promisify(execFile);
const root = join(homedir(), 'Library', 'Application Support', 'YanusBackup');
const bin = join(root, 'bin');
const archives = join(root, 'archives');
const logs = join(root, 'logs');
for (const directory of [root, bin, archives, logs]) { await mkdir(directory, { recursive: true, mode: 0o700 }); await chmod(directory, 0o700); }
const source = dirname(fileURLToPath(import.meta.url));
for (const file of ['collect.mjs', 'lock.mjs', 'metadata.mjs', 'ssh-transport.mjs', 'key-recovery.mjs', 'windows-key-recovery.ps1']) {
  await copyFile(join(source, file), join(bin, file)); await chmod(join(bin, file), 0o600);
}
const label = 'kr.bond.yanus.db-backup.collector';
const plist = join(homedir(), 'Library', 'LaunchAgents', `${label}.plist`);
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
await mkdir(dirname(plist), { recursive: true });
await writeFile(plist, `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>Label</key><string>${label}</string>
<key>ProgramArguments</key><array>${[process.execPath, join(bin, 'collect.mjs'), archives, 'data-server'].map(value => `<string>${escape(value)}</string>`).join('')}</array>
<key>RunAtLoad</key><true/><key>StartInterval</key><integer>3600</integer>
<key>EnvironmentVariables</key><dict><key>PATH</key><string>/usr/bin:/bin:/usr/local/bin:/opt/homebrew/bin</string></dict>
<key>StandardOutPath</key><string>${escape(join(logs, 'collector.log'))}</string>
<key>StandardErrorPath</key><string>${escape(join(logs, 'collector-error.log'))}</string>
</dict></plist>`, { mode: 0o600 });
await chmod(plist, 0o600);
await execute('/usr/bin/plutil', ['-lint', plist]);
const domain = `gui/${process.getuid()}`;
try { await execute('/bin/launchctl', ['bootout', `${domain}/${label}`]); }
catch (error) { if (!/No such process|Could not find service|not found/i.test(error.stderr || '')) throw error; }
await execute('/bin/launchctl', ['bootstrap', domain, plist]);
console.log(JSON.stringify({ status: 'INSTALLED', interval_seconds: 3600, root }));
