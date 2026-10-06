import { createHash } from 'node:crypto';

const files = ['database.dump', 'roles.sql', 'verification.json', 'manifest.json'];
export function unpackArchive(buffer, expectedId) {
  if (buffer.length > 128 * 1024 * 1024 || buffer.length % 512 !== 0) throw new Error('INVALID_ARCHIVE_SIZE');
  const entries = new Map();
  let offset = 0;
  while (offset + 512 <= buffer.length) {
    const header = buffer.subarray(offset, offset + 512);
    if (header.every(byte => byte === 0)) break;
    const text = (start, end) => header.subarray(start, end).toString('utf8').split('\0')[0];
    const name = text(0, 100);
    const rawSize = text(124, 136).trim();
    const rawChecksum = text(148, 156).trim();
    const checksum = header.reduce((sum, byte, index) => sum + (index >= 148 && index < 156 ? 32 : byte), 0);
    if (!files.includes(name) || entries.has(name) || ![0, 48].includes(header[156])
        || text(157, 257) || text(345, 500) || !/^[0-7]+$/.test(rawSize)
        || !/^[0-7]+$/.test(rawChecksum) || parseInt(rawChecksum, 8) !== checksum) throw new Error('UNSAFE_ARCHIVE_ENTRY');
    const size = parseInt(rawSize, 8);
    if (offset + 512 + size > buffer.length || (name !== 'database.dump' && size > 4 * 1024 * 1024)) throw new Error('TRUNCATED_ARCHIVE');
    entries.set(name, buffer.subarray(offset + 512, offset + 512 + size));
    offset += 512 + Math.ceil(size / 512) * 512;
  }
  if (entries.size !== 4 || buffer.length - offset < 1024 || !buffer.subarray(offset).every(byte => byte === 0)) throw new Error('INCOMPLETE_ARCHIVE');
  const manifest = JSON.parse(entries.get('manifest.json').toString('utf8'));
  if (manifest.version !== 1 || manifest.id !== expectedId || !/^[a-z][a-z0-9_]{0,62}$/.test(manifest.database)) throw new Error('INVALID_MANIFEST');
  for (const name of files.filter(name => name !== 'manifest.json')) {
    if (createHash('sha256').update(entries.get(name)).digest('hex') !== manifest.files?.[name]) throw new Error('INTERNAL_CHECKSUM_MISMATCH');
  }
  const verification = JSON.parse(entries.get('verification.json').toString('utf8'));
  if (Math.floor(verification.server_version_num / 10000) !== 16 || !/^[a-z][a-z0-9_]{0,62}$/.test(verification.database_owner)
      || !Array.isArray(verification.tables) || !Array.isArray(verification.history)
      || !Array.isArray(verification.constraints) || !Array.isArray(verification.ownership)) throw new Error('INCOMPATIBLE_VERIFICATION');
  for (const table of verification.tables) {
    if (table.schema !== 'public' || typeof table.table !== 'string' || !table.table
        || !Number.isSafeInteger(table.rows) || table.rows < 0) throw new Error('INVALID_TABLE_VERIFICATION');
  }
  return { entries, manifest, verification };
}
