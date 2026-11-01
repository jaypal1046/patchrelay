import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJson, loadBaseline, inside, sha256, fingerprint, checkNative, checkFunctions, command } from '../cli/src/project.mjs';
import { verifyBuild } from '../cli/src/main.mjs';

// Build-result validation for this repository; this never selects a patch baseline.
const root = fileURLToPath(new URL('../', import.meta.url));
const [module = 'demo', variant = 'release', type = 'apk'] = process.argv.slice(2);
assert.ok(['apk', 'aab'].includes(type));
const result = readJson(inside(root, `${module}/build/outputs/patchrelay/${variant}/${type}-record.json`));
const file = inside(root, result.baselineFile);
const baseline = loadBaseline(file);
const runtime = readJson(resolve(root, 'runtime.json'));
checkFunctions(baseline.functionContract, readJson(inside(root, 'screens/build/generated/patchrelay/functions.json')));
assert.equal(baseline.artifactType, type);
assert.equal(baseline.appId, runtime.appId);
assert.equal(baseline.baseVersion, runtime.baseVersion);
checkNative(baseline.nativeFiles, fingerprint(root, runtime.nativeInputs, runtime.textExtensions));
checkNative(baseline.screenFiles, fingerprint(root, ['screens/src/commonMain'], runtime.textExtensions));
assert.equal(sha256(readFileSync(inside(root, result.artifactFile))), baseline.artifactSha256);
assert.equal(sha256(readFileSync(resolve(dirname(file), `screen-base.${type}`))), baseline.artifactSha256);
assert.equal(sha256(readFileSync(resolve(dirname(file), 'baseline-screen.js'))), baseline.programSha256);
if (module === 'demo' && variant === 'release') {
    verifyBuild({root, settings: readJson(inside(root, 'patchrelay.config.json')), runtime}, baseline.hostSha256);
    const consumer = command(root, 'javap', ['-c', '-p', inside(root, 'demo/build/tmp/kotlin-classes/release/dev/patchrelay/screens/demo/DemoApp.class')], true);
    assert.match(consumer, /getstatic[^\n]*BuildConfig\.HOST_SHA256/, 'Native updater/state store must read the installed fingerprint rather than inline an older value');
}
console.log(`Verified normal Gradle ${type.toUpperCase()} baseline: ${result.baselineFile}`);
