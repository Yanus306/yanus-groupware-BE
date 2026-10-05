import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createHash } from 'node:crypto';
import fs from 'node:fs/promises';

const execute = promisify(execFile);
export const digest = bytes => createHash('sha256').update(bytes).digest('hex');
export function migrationChecksum(text) {
  let crc = 0xffffffff;
  for (const byte of Buffer.from(text.replace(/^\uFEFF/, '').replace(/\r\n|\r|\n/g, ''), 'utf8')) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) | 0;
}
export async function jarMetadata(file) {
  if ((await fs.stat(file)).size > 268435456) throw new Error('JAR_SIZE_LIMIT');
  const data = await fs.readFile(file);
  const { stdout } = await execute('unzip', ['-Z1', file], { timeout: 10000, maxBuffer: 1048576 });
  const entries = stdout.trim().split('\n');
  if (new Set(entries).size !== entries.length) throw new Error('DUPLICATE_JAR_ENTRY');
  const migrations = [];
  for (const entry of entries.filter(name => /^BOOT-INF\/classes\/db\/migration\/[^/]+$/.test(name)).sort()) {
    const script = entry.split('/').at(-1);
    const version = /^V([0-9]+(?:[._][0-9]+)*)__.+\.sql$/.exec(script)?.[1]?.replaceAll('_', '.');
    if (!version) throw new Error('UNSUPPORTED_MIGRATION');
    const { stdout: sql } = await execute('unzip', ['-p', file, entry], { timeout: 10000, maxBuffer: 1048576 });
    migrations.push({ version, script, checksum: migrationChecksum(sql), sha256: digest(sql) });
  }
  let commit = null;
  const buildInfo = ['META-INF/build-info.properties', 'BOOT-INF/classes/META-INF/build-info.properties'].find(entry => entries.includes(entry));
  if (buildInfo) {
    const { stdout: properties } = await execute('unzip', ['-p', file, buildInfo], { timeout: 10000 });
    commit = /^build\.commit=([a-f0-9]{40})$/m.exec(properties)?.[1] ?? null;
  }
  return { sha256: digest(data), size: data.length, commit, migrations };
}
