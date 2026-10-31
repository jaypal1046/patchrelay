import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJson, loadBaseline, inside, sha256, fingerprint, checkNative } from '../cli/src/project.mjs';

// Build-result validation for this repository; this never selects a patch baseline.
const root = fileURLToPath(new URL('../', import.meta.url));
const [module = 'demo', variant = 'release', type = 'apk'] = process.argv.slice(2);
assert.ok(['apk', 'aab'].includes(type));
const result = readJson(inside(root, `${module}/build/outputs/patchrelay/${variant}/${type}-record.json`));
const file = inside(root, result.baselineFile);
const baseline = loadBaseline(file);
const runtime = readJson(resolve(root, 'runtime.json'));
assert.equal(baseline.artifactType, type);
assert.equal(baseline.appId, runtime.appId);
assert.equal(baseline.baseVersion, runtime.baseVersion);
checkNative(baseline.nativeFiles, fingerprint(root, runtime.nativeInputs, runtime.textExtensions));
checkNative(baseline.screenFiles, fingerprint(root, ['screens/src/commonMain'], runtime.textExtensions));
assert.equal(sha256(readFileSync(inside(root, result.artifactFile))), baseline.artifactSha256);
assert.equal(sha256(readFileSync(resolve(dirname(file), `screen-base.${type}`))), baseline.artifactSha256);
assert.equal(sha256(readFileSync(resolve(dirname(file), 'baseline-screen.js'))), baseline.programSha256);
console.log(`Verified normal Gradle ${type.toUpperCase()} baseline: ${result.baselineFile}`);
