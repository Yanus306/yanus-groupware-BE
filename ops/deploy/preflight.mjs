import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { jarMetadata, digest } from './jar-metadata.mjs';

export function checkCompatibility(manifest, candidate, baseline, current, history) {
  assert.equal(manifest.version, 1, 'MANIFEST_VERSION');
  assert.match(manifest.commit ?? '', /^[a-f0-9]{40}$/, 'COMMIT_REQUIRED');
  assert.equal(manifest.sha256, candidate.sha256, 'CHECKSUM_MISMATCH');
  assert.equal(manifest.size, candidate.size, 'SIZE_MISMATCH');
  assert.equal(manifest.commit, candidate.commit, 'JAR_BUILD_IDENTITY_MISMATCH');
  assert.equal(manifest.observationMode, 'native', 'UNSUPPORTED_OBSERVATION_MODE');
  assert.equal(manifest.schemaPolicy, 'unchanged', 'SCHEMA_REVIEW_REQUIRED');
  assert.equal(baseline.compatibilityApproved, true, 'COMPATIBILITY_APPROVAL_REQUIRED');
  assert.match(baseline.sourceCommit ?? '', /^[a-f0-9]{40}$/, 'BASELINE_SOURCE_UNKNOWN');
  assert.equal(baseline.sha256, current.sha256, 'BASELINE_CHANGED');
  assert.equal(baseline.schemaSha256, digest(JSON.stringify(history)), 'SCHEMA_HISTORY_CHANGED');
  assert.deepEqual(manifest.migrations, candidate.migrations, 'MANIFEST_INVENTORY_MISMATCH');
  assert.deepEqual(candidate.migrations, current.migrations, 'SCHEMA_CHANGE_BLOCKED');
  const sql = history.filter(row => row.type === 'SQL');
  assert.ok(history.every(row => row.success === true && ['SQL', 'BASELINE'].includes(row.type)), 'FAILED_OR_UNSUPPORTED_SCHEMA_HISTORY');
  assert.equal(sql.length, candidate.migrations.length, 'UNAPPLIED_MIGRATION');
  for (const migration of candidate.migrations) {
    const installed = sql.find(row => row.version === migration.version && row.script === migration.script);
    assert.ok(installed && installed.checksum === migration.checksum, 'MIGRATION_CHECKSUM_MISMATCH');
  }
}

if (process.argv[1]?.endsWith('/preflight.mjs')) {
  const [directory, currentJar, baselineFile, historyFile] = process.argv.slice(2);
  const [manifest, baseline, history, candidate, current] = await Promise.all([
    fs.readFile(`${directory}/manifest.json`, 'utf8').then(JSON.parse),
    fs.readFile(baselineFile, 'utf8').then(JSON.parse), fs.readFile(historyFile, 'utf8').then(JSON.parse),
    jarMetadata(`${directory}/app.jar`), jarMetadata(currentJar),
  ]);
  try { checkCompatibility(manifest, candidate, baseline, current, history); }
  catch (error) { console.error(JSON.stringify({ status: 'PREFLIGHT_BLOCKED', reason: error.message.split('\n')[0] })); process.exit(1); }
  console.log(JSON.stringify({ status: 'PREFLIGHT_OK', commit: candidate.commit, sha256: candidate.sha256 }));
}
