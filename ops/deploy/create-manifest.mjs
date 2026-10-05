import fs from 'node:fs/promises';
import { jarMetadata } from './jar-metadata.mjs';

const [jar, output] = process.argv.slice(2);
if (!jar || !output || !/^[a-f0-9]{40}$/.test(process.env.GITHUB_SHA ?? '')) throw new Error('Full GITHUB_SHA and JAR/output required');
const metadata = await jarMetadata(jar);
if (metadata.commit !== process.env.GITHUB_SHA) throw new Error('JAR_BUILD_IDENTITY_MISMATCH');
await fs.writeFile(output, JSON.stringify({ version: 1, ...metadata, observationMode: 'native', schemaPolicy: 'unchanged', createdAt: new Date().toISOString() }, null, 2) + '\n');
console.log(JSON.stringify({ commit: metadata.commit, sha256: metadata.sha256, size: metadata.size, migrations: metadata.migrations.length }));
