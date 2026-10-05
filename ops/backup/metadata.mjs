import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';

export const ID_PATTERN = /^\d{8}T\d{6}Z-[a-f0-9]{16}$/;

export function parseMetadata(line) {
  const value = JSON.parse(line);
  if (value === null || typeof value !== 'object' || Array.isArray(value)
      || value.version !== 1 || typeof value.id !== 'string' || !ID_PATTERN.test(value.id)
      || typeof value.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(value.sha256)
      || !Number.isSafeInteger(value.bytes) || value.bytes < 1 || value.bytes > 2 ** 31
      || !Number.isSafeInteger(value.snapshot_epoch) || value.snapshot_epoch < 1
      || value.snapshot_epoch > Date.now() / 1000 + 300) {
    throw new Error('INVALID_METADATA');
  }
  return { version: 1, id: value.id, sha256: value.sha256, bytes: value.bytes, snapshot_epoch: value.snapshot_epoch };
}

export async function sha256(path) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest('hex');
}
