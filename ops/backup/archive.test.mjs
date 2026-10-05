import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { unpackArchive } from './archive.mjs';
import { validateDrillConfig } from './restore-drill.mjs';

const id = '20261005T120000Z-0123456789abcdef';
function archive(change = {}) {
  const contents = { 'database.dump': Buffer.from('fixture dump'), 'roles.sql': Buffer.from('fixture roles'),
    'verification.json': Buffer.from(JSON.stringify({ server_version_num: change.version ?? 160015,
      database_owner: 'yanus', tables: [], history: [], constraints: [], ownership: [] })) };
  contents['manifest.json'] = Buffer.from(JSON.stringify({ version: 1, id, database: 'yanus',
    files: Object.fromEntries(Object.entries(contents).map(([name, bytes]) => [name, createHash('sha256').update(bytes).digest('hex')])) }));
  const blocks = [];
  for (const [original, bytes] of Object.entries(contents)) {
    const header = Buffer.alloc(512);
    header.write(change.name && original === 'database.dump' ? change.name : original);
    header.write(`${bytes.length.toString(8).padStart(11, '0')}\0`, 124);
    header[156] = change.type && original === 'database.dump' ? change.type : 48;
    header.fill(32, 148, 156);
    header.write(`${header.reduce((sum, value) => sum + value, 0).toString(8).padStart(6, '0')}\0 `, 148);
    blocks.push(header, bytes, Buffer.alloc(Math.ceil(bytes.length / 512) * 512 - bytes.length));
  }
  return Buffer.concat([...blocks, Buffer.alloc(1024)]);
}

test('허용한 네 파일과 내부 checksum·PG16 metadata만 복원 입력으로 받는다', () => {
  assert.equal(unpackArchive(archive(), id).manifest.database, 'yanus');
});
test('경로 조작·symlink·다른 DB major·다른 archive ID를 거절한다', () => {
  assert.throws(() => unpackArchive(archive({ name: '../../identity.txt' }), id), /UNSAFE_ARCHIVE_ENTRY/);
  assert.throws(() => unpackArchive(archive({ type: 50 }), id), /UNSAFE_ARCHIVE_ENTRY/);
  assert.throws(() => unpackArchive(archive({ version: 170000 }), id), /INCOMPATIBLE_VERIFICATION/);
  assert.throws(() => unpackArchive(archive(), 'other-id'), /INVALID_MANIFEST/);
});
test('내부 dump 손상과 불완전 tar를 거절한다', () => {
  const changed = archive();
  changed[512] ^= 1;
  assert.throws(() => unpackArchive(changed, id), /INTERNAL_CHECKSUM_MISMATCH/);
  assert.throws(() => unpackArchive(archive().subarray(0, 512), id), /TRUNCATED_ARCHIVE/);
  assert.throws(() => unpackArchive(archive().subarray(0, -1024), id), /INCOMPLETE_ARCHIVE/);
});
test('운영 DB·호스트·URL을 받는 복원 옵션은 허용하지 않는다', () => {
  const config = { root: '/fixture', id, identity: '/fixture-key', jar: '/fixture.jar' };
  validateDrillConfig(config);
  for (const option of ['target', 'host', 'databaseUrl']) assert.throws(() => validateDrillConfig({ ...config, [option]: 'prod' }), /ISOLATED_TARGET_ONLY/);
});
