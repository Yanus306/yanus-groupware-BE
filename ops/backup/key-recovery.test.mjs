import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmod, mkdir, mkdtemp, rm, symlink, unlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

test('unsafe source permissions and symlinks never reach SSH or print key material', async () => {
  const root = await mkdtemp(join(tmpdir(), 'yanus-key-test-'));
  const identity = join(root, 'keys', 'identity.txt');
  const marker = 'fixture-secret-must-not-be-printed';
  try {
    await mkdir(join(root, 'keys'), { mode: 0o700 });
    await writeFile(identity, marker, { mode: 0o600 });
    await chmod(identity, 0o644);
    const result = spawnSync(process.execPath, ['ops/backup/key-recovery.mjs', 'store', root], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /UNSAFE_IDENTITY/);
    assert.ok(!`${result.stdout}${result.stderr}`.includes(marker));
    await unlink(identity);
    await symlink(join(root, 'recovery-recipient.txt'), identity);
    await writeFile(join(root, 'recovery-recipient.txt'), marker, { mode: 0o600 });
    const link = spawnSync(process.execPath, ['ops/backup/key-recovery.mjs', 'store', root], { encoding: 'utf8' });
    assert.equal(link.status, 1);
    assert.match(link.stderr, /UNSAFE_IDENTITY/);
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('invalid public recipient refuses recovery before SSH', async () => {
  const root = await mkdtemp(join(tmpdir(), 'yanus-key-test-'));
  try {
    await writeFile(join(root, 'recovery-recipient.txt'), 'not-a-recipient');
    const result = spawnSync(process.execPath, ['ops/backup/key-recovery.mjs', 'recover', root], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /INVALID_RECIPIENT/);
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('unknown action reports a bounded code without echoing arguments', () => {
  const result = spawnSync(process.execPath, ['ops/backup/key-recovery.mjs', 'fixture-secret'], { encoding: 'utf8' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /USAGE_STORE_VERIFY_RECOVER/);
  assert.ok(!result.stderr.includes('fixture-secret'));
});
