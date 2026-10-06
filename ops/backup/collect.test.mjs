import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { collect } from './collect.mjs';
import { parseMetadata } from './metadata.mjs';
import { acquireLock } from './lock.mjs';

const directories = [];
afterEach(async () => { await Promise.all(directories.splice(0).map(path => rm(path, { recursive: true, force: true }))); });
async function fixture() {
  const root = await mkdtemp(join(tmpdir(), 'yanus-collector-'));
  directories.push(root);
  const payload = Buffer.from('fixture encrypted archive');
  const item = { version: 1, id: '20261005T120000Z-0123456789abcdef',
    bytes: payload.length, sha256: createHash('sha256').update(payload).digest('hex'), snapshot_epoch: 1791201600 };
  const receipts = [];
  const transport = { async list() { return `${JSON.stringify(item)}\n`; },
    async archive(_id, destination) { await writeFile(destination, payload, { flag: 'wx' }); },
    async ack(value) { receipts.push(value.id); } };
  return { root, payload, item, receipts, transport, config: { root, host: 'fixture', retentionDays: 14 } };
}

test('검증한 사본을 저장한 뒤 ACK하고 재실행해도 파일을 보존한다', async () => {
  // Given
  const f = await fixture();
  // When
  await collect(f.config, f.transport);
  await collect(f.config, f.transport);
  // Then
  assert.deepEqual(await readFile(join(f.root, `${f.item.id}.age`)), f.payload);
  assert.deepEqual(f.receipts, [f.item.id, f.item.id]);
});

test('전송 checksum 불일치에는 ACK하지 않고 임시 파일을 정리한다', async () => {
  // Given
  const f = await fixture();
  f.transport.archive = async (_id, destination) => writeFile(destination, 'incorrect');
  // When
  await assert.rejects(collect(f.config, f.transport), /CHECKSUM_MISMATCH/);
  // Then
  assert.deepEqual(f.receipts, []);
  assert.deepEqual((await readdir(f.root)).filter(name => name.endsWith('.part') || name.endsWith('.age')), []);
});

test('ACK 실패 후 재실행하면 기존 유효 사본으로 전달을 다시 확인한다', async () => {
  // Given
  const f = await fixture();
  const failed = { ...f.transport, async ack() { throw new Error('fixture offline'); } };
  await assert.rejects(collect(f.config, failed));
  // When
  await collect(f.config, f.transport);
  // Then
  assert.deepEqual(f.receipts, [f.item.id]);
});

test('전송 timeout에는 기존 성공 기록을 보존하고 실패를 기록한다', async () => {
  // Given
  const f = await fixture();
  const previous = JSON.stringify({ ...f.item, snapshot_epoch: f.item.snapshot_epoch - 1 });
  await writeFile(join(f.root, 'last-success.json'), previous);
  f.transport.archive = async () => { throw new Error('fixture timeout'); };
  // When
  await assert.rejects(collect(f.config, f.transport));
  // Then
  assert.equal(await readFile(join(f.root, 'last-success.json'), 'utf8'), previous);
  assert.equal(JSON.parse(await readFile(join(f.root, 'collection-status.json'), 'utf8')).status, 'FAILED');
});

test('중복 수집은 원래 잠금을 지우거나 ACK하지 않는다', async () => {
  // Given
  const f = await fixture();
  const release = await acquireLock(join(f.root, 'collection.lock'));
  // When
  try { await assert.rejects(collect(f.config, f.transport), { code: 'EEXIST' }); }
  finally { await release(); }
  // Then
  assert.deepEqual(f.receipts, []);
});

test('오래된 서버 목록을 재수집해도 최신 Mac 복구 시점은 뒤로 가지 않는다', async () => {
  const f = await fixture();
  await collect(f.config, f.transport);
  const old = { ...f.item, id: '20260101T000000Z-0123456789abcdef', snapshot_epoch: 1767225600 };
  f.transport.list = async () => JSON.stringify(old);
  await collect(f.config, f.transport);
  assert.equal(JSON.parse(await readFile(join(f.root, 'last-success.json'), 'utf8')).id, f.item.id);
  assert.deepEqual(await readFile(join(f.root, `${f.item.id}.age`)), f.payload);
});

test('이전 실행의 잠금 파일이 남아도 실제 잠금이 없으면 수집한다', async () => {
  const f = await fixture();
  await writeFile(join(f.root, 'collection.lock'), '999999999\n');
  await collect(f.config, f.transport);
  assert.deepEqual(f.receipts, [f.item.id]);
});

test('이전 snapshot ACK는 새 백업으로 위장할 수 없다', () => {
  // Given
  const old = { version: 1, id: '20260101T000000Z-0123456789abcdef', bytes: 10,
    sha256: 'a'.repeat(64), snapshot_epoch: 1767225600 };
  // When
  const result = parseMetadata(JSON.stringify(old));
  // Then
  assert.equal(result.snapshot_epoch, 1767225600);
});

test('조작된 경로·미래 시각·잘못된 크기·중복 ID는 거절한다', async () => {
  // Given
  const f = await fixture();
  // When / Then
  for (const change of [{ id: '../../secret' }, { snapshot_epoch: 9999999999 }, { bytes: -1 }]) {
    assert.throws(() => parseMetadata(JSON.stringify({ ...f.item, ...change })), /INVALID_METADATA/);
  }
  f.transport.list = async () => `${JSON.stringify(f.item)}\n${JSON.stringify(f.item)}`;
  await assert.rejects(collect(f.config, f.transport), /DUPLICATE_METADATA/);
  assert.deepEqual(f.receipts, []);
});
