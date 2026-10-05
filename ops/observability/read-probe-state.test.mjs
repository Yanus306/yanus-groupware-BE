import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execute = promisify(execFile);
const script = fileURLToPath(new URL('./read-probe-state.mjs', import.meta.url));
test('scheduled history loads only named artifact; unavailable history clears stale local state', async t => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'yanus-history-test-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  const gh = path.join(dir, 'gh');
  await fs.writeFile(gh, `#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const args = process.argv.slice(2);
fs.appendFileSync('invocations.jsonl', JSON.stringify(args) + '\\n');
if (process.env.HISTORY_FAIL === '1') process.exit(1);
if (args[1] === 'list') console.log('[{"databaseId":123}]');
else fs.writeFileSync(path.join(args[args.indexOf('--dir') + 1], 'probe-state.json'), '[]');
`, { mode: 0o700 });
  const env = { ...process.env, GITHUB_REPOSITORY: 'Yanus306/yanus-groupware-BE', PATH: `${dir}${path.delimiter}${process.env.PATH}` };
  await execute(process.execPath, [script], { cwd: dir, env });
  assert.equal(await fs.readFile(path.join(dir, 'previous-probe-state.json'), 'utf8'), '[]');
  const calls = (await fs.readFile(path.join(dir, 'invocations.jsonl'), 'utf8')).trim().split('\n').map(line => JSON.parse(line));
  assert.ok(calls[0].includes('schedule') && calls[0].includes('main') && calls[0].includes('uptime.yml'));
  assert.ok(calls[1].includes('probe-state-prod') && calls[1].includes('123'));
  await execute(process.execPath, [script], { cwd: dir, env: { ...env, HISTORY_FAIL: '1' } });
  await assert.rejects(fs.access(path.join(dir, 'previous-probe-state.json')));
});
