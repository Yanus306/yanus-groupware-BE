import { chmod, lstat, mkdir, open, readFile, readdir, rename, rm, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { ID_PATTERN, parseMetadata, sha256 } from './metadata.mjs';
import { sshTransport } from './ssh-transport.mjs';
import { acquireLock } from './lock.mjs';

async function atomicJson(path, value) {
  const temporary = `${path}.${process.pid}.part`;
  try {
    const handle = await open(temporary, 'wx', 0o600);
    try { await handle.writeFile(`${JSON.stringify(value)}\n`); await handle.sync(); }
    finally { await handle.close(); }
    await rename(temporary, path);
  } finally { await rm(temporary, { force: true }); }
}

async function fileExists(path) {
  try { await lstat(path); return true; }
  catch (error) { if (error.code === 'ENOENT') return false; throw error; }
}

export async function collect(config, transport = sshTransport(config.host)) {
  const root = resolve(config.root);
  await mkdir(root, { recursive: true, mode: 0o700 });
  if (!(await lstat(root)).isDirectory() || (await lstat(root)).isSymbolicLink()) throw new Error('INVALID_ROOT');
  await chmod(root, 0o700);
  const release = await acquireLock(join(root, 'collection.lock'));
  try {
    const metadata = (await transport.list()).split('\n').filter(line => line.trim()).map(parseMetadata)
      .sort((a, b) => a.snapshot_epoch - b.snapshot_epoch);
    if (new Set(metadata.map(item => item.id)).size !== metadata.length) throw new Error('DUPLICATE_METADATA');
    let newest;
    const previous = join(root, 'last-success.json');
    if (await fileExists(previous)) newest = parseMetadata(await readFile(previous, 'utf8'));
    for (const item of metadata) {
      const destination = join(root, `${item.id}.age`);
      const temporary = `${destination}.${process.pid}.part`;
      if (await fileExists(destination)) {
        const info = await lstat(destination);
        if (!info.isFile() || info.isSymbolicLink() || info.size !== item.bytes
            || await sha256(destination) !== item.sha256) throw new Error('EXISTING_COPY_CORRUPT');
      } else {
        try {
          await transport.archive(item.id, temporary);
          if ((await stat(temporary)).size !== item.bytes || await sha256(temporary) !== item.sha256) {
            throw new Error('CHECKSUM_MISMATCH');
          }
          const archive = await open(temporary, 'r+');
          try { await archive.sync(); } finally { await archive.close(); }
          await rename(temporary, destination);
        } finally { await rm(temporary, { force: true }); }
      }
      await atomicJson(join(root, `${item.id}.json`), item);
      await transport.ack(item);
      if (!newest || item.snapshot_epoch >= newest.snapshot_epoch) {
        newest = item;
        await atomicJson(previous, { ...item, receipt_epoch: Math.floor(Date.now() / 1000) });
      }
    }
    if (newest) {
      const cutoff = Date.now() / 1000 - config.retentionDays * 86400;
      for (const name of await readdir(root)) {
        if (!name.endsWith('.json') || !ID_PATTERN.test(name.slice(0, -5))) continue;
        const item = parseMetadata(await readFile(join(root, name), 'utf8'));
        if (item.id !== newest.id && item.snapshot_epoch < cutoff) {
          await rm(join(root, `${item.id}.age`), { force: true });
          await rm(join(root, name));
        }
      }
    }
    await atomicJson(join(root, 'collection-status.json'), {
      status: 'SUCCESS', checked_epoch: Math.floor(Date.now() / 1000), count: metadata.length,
    });
    return metadata.length;
  } catch (error) {
    await atomicJson(join(root, 'collection-status.json'), { status: 'FAILED', checked_epoch: Math.floor(Date.now() / 1000) });
    throw error;
  } finally {
    await release();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const root = process.argv[2];
  const host = process.argv[3];
  if (!root || !host) { console.error('사용법: node collect.mjs ARCHIVE_DIRECTORY SSH_ALIAS'); process.exitCode = 64; }
  else {
    try {
      const count = await collect({ root, host, retentionDays: 14 });
      console.log(JSON.stringify({ status: 'SUCCESS', archives: count }));
    } catch {
      console.error('백업 수집 실패: 보호된 상태 파일과 SSH 연결·checksum을 확인하세요.');
      process.exitCode = 1;
    }
  }
}
