import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, cpSync, existsSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { main, verifyBuild } from '../src/main.mjs';
import { sha256, hostHash, fingerprint, inside, checkNative, loadBaseline, signedManifest, validateUrl, command, routeId, updateCatalog, signPayload, verifiedEnvelope, reserveVersion, baselineFiles } from '../src/project.mjs';

const keypair = generateKeyPairSync('rsa', { modulusLength: 3072 });
const key = Buffer.from(keypair.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64'));
const baseline = { format: 3, appId: 'test.app', baseVersion: 'v1', screenApi: 2, screenIds: ['checkout'],
  nativeFiles: { 'native.txt': 'hash' }, screenFiles: {}, hostSha256: hostHash({ 'native.txt': 'hash' }) };

function temporary(run) {
  const root = mkdtempSync(join(tmpdir(), 'patchrelay-cli-'));
  return Promise.resolve().then(() => run(root)).finally(() => rmSync(root, { recursive: true, force: true }));
}

test('fingerprints preserve Python compatibility including Unicode and line endings', () => temporary(root => {
  assert.equal(hostHash({ 'é': '😀', a: '1' }), sha256('{"a":"1","\\u00e9":"\\ud83d\\ude00"}'));
  writeFileSync(join(root, 'native.txt'), 'before\r\n');
  const first = fingerprint(root, ['native.txt'], ['.txt']);
  writeFileSync(join(root, 'native.txt'), 'before\n');
  assert.deepEqual(first, fingerprint(root, ['native.txt'], ['.txt']));
  writeFileSync(join(root, 'native.txt'), 'after');
  assert.throws(() => checkNative(first, fingerprint(root, ['native.txt'], ['.txt'])), /new base/);
  assert.throws(() => fingerprint(root, ['missing'], []));
  assert.throws(() => inside(root, '../escape'), /escapes/);
  writeFileSync(join(root, 'LICENSE'), 'terms\r\n');
  const legal = fingerprint(root, ['LICENSE'], ['LICENSE']);
  writeFileSync(join(root, 'LICENSE'), 'terms\n');
  assert.deepEqual(legal, fingerprint(root, ['LICENSE'], ['LICENSE']));
}));

test('signatures bind the exact artifact, app and base using the existing RSA format', () => {
  const bytes = Buffer.from('example program');
  const envelope = signedManifest(baseline, 7, 'https://example.com/screens.js', bytes, key);
  const payload = Buffer.from(envelope.payload, 'base64');
  assert.equal(verify('sha256', payload, keypair.publicKey, Buffer.from(envelope.signature, 'base64')), true);
  assert.equal(verify('sha256', Buffer.concat([payload, Buffer.from('x')]), keypair.publicKey, Buffer.from(envelope.signature, 'base64')), false);
  const record = JSON.parse(payload);
  assert.equal(record.version, 7);
  assert.equal(record.sha256, sha256(bytes));
  assert.equal(record.appId, 'test.app');
  for (const version of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => signedManifest(baseline, version, 'https://example.com/s', bytes, key));
  assert.throws(() => signedManifest(baseline, 1, 'https://example.com/s', Buffer.from([0xff]), key));
  assert.throws(() => signedManifest(baseline, 1, 'https://example.com/s', Buffer.alloc(2 * 1024 * 1024 + 1), key));
  for (const url of ['http://example.com/s', 'https://user:pass@example.com/s', 'https://example.com/s#fragment', 'not a URL']) assert.throws(() => validateUrl(url));
  assert.throws(() => signedManifest(baseline, 1, 'https://example.com/s', bytes, key, Buffer.from('wrong key')), /does not match/);
});

test('baseline corruption and missing archives are rejected', () => temporary(root => {
  const record = { ...baseline, sourcesSha256: sha256('archive') };
  writeFileSync(join(root, 'baseline.json'), JSON.stringify(record));
  assert.throws(() => loadBaseline(join(root, 'baseline.json')), /missing or altered/);
  writeFileSync(join(root, 'baseline-sources.zip'), 'archive');
  assert.deepEqual(loadBaseline(join(root, 'baseline.json')), record);
  writeFileSync(join(root, 'baseline-sources.zip'), 'changed');
  assert.throws(() => loadBaseline(join(root, 'baseline.json')), /missing or altered/);
}));

test('baseline listing finds named and nested Gradle records without selecting one', () => temporary(root => {
  const files = ['named/baseline.json', 'test.app/release/apk-hash/baseline.json', 'test.app/release/aab-hash/baseline.json'];
  for (const file of files) { mkdirSync(dirname(join(root, file)), { recursive: true }); writeFileSync(join(root, file), '{}'); }
  writeFileSync(join(root, 'unverified-baseline.json'), '{}');
  assert.deepEqual(baselineFiles(root), files.map(file => join(root, file)).sort());
  assert.deepEqual(baselineFiles(join(root, 'missing')), []);
}));

test('guided init stores only settings and never overwrites them', () => temporary(async root => {
  writeFileSync(join(root, 'runtime.json'), JSON.stringify({ appId: 'test.app', nativeInputs: ['app/src/main'] }));
  mkdirSync(join(root, 'gradle-plugin'));
  writeFileSync(join(root, 'gradle-plugin/build.gradle'), 'unrelated build plugin');
  writeFileSync(join(root, 'local.properties'), 'sdk.dir=existing-sdk\n');
  const answers = ['app', 'screens', 'owner/repository', '.keys/private.key', 'https://updates.example.com/catalog.json', 'production'];
  await main(['init'], root, async () => answers.shift());
  const saved = JSON.parse(readFileSync(join(root, 'patchrelay.config.json')));
  assert.equal(saved.repository, 'owner/repository');
  assert.equal(saved.signingKey, '.keys/private.key');
  assert.ok(readFileSync(join(root, '.gitignore'), 'utf8').includes('.patchrelay/'));
  assert.ok(readFileSync(join(root, 'local.properties'), 'utf8').startsWith('sdk.dir=existing-sdk\n'));
  assert.ok(readFileSync(join(root, 'local.properties'), 'utf8').includes('patchrelay.catalogUrl=https://updates.example.com/catalog.json'));
  assert.ok(existsSync(join(root, 'patchrelay-gradle-plugin/src/main/groovy/dev/patchrelay/gradle/PatchRelayPlugin.groovy')));
  assert.equal(readFileSync(join(root, 'gradle-plugin/build.gradle'), 'utf8'), 'unrelated build plugin');
  assert.ok(JSON.parse(readFileSync(join(root, 'runtime.json'))).nativeInputs.includes('patchrelay-gradle-plugin/src/main'));
  await assert.rejects(main(['init'], root, async () => assert.fail('must not prompt')), /already configured/);
  await assert.rejects(main(['patch', '--from', 'HEAD'], root), /Invalid option/);
}));

test('CLI verifies Gradle-generated host constants and rejects mismatched output', () => temporary(root => {
  const path = 'app/build/generated/source/buildConfig/release/test/app/BuildConfig.java';
  mkdirSync(dirname(join(root, path)), { recursive: true });
  writeFileSync(join(root, path), 'HOST_SHA256 = test.namespace.patchrelay.PatchRelayBuild.HOST_SHA256; APPLICATION_ID = "test.app"; BASE_VERSION = "v1";');
  const generated = join(root, 'app/build/generated/patchrelay/release/test/namespace/patchrelay/PatchRelayBuild.java');
  mkdirSync(dirname(generated), { recursive: true });
  writeFileSync(generated, `HOST_SHA256 = "${'b'.repeat(64)}";`);
  const ctx = { root, settings: { buildConfigFile: path }, runtime: { appId: 'test.app', baseVersion: 'v1' } };
  verifyBuild(ctx, 'b'.repeat(64));
  assert.throws(() => verifyBuild(ctx, 'c'.repeat(64)), /HOST_SHA256/);
}));

test('signed catalogs preserve old bases and reject app/channel confusion, tampering and downgrade', () => {
  const publicKey = Buffer.from(keypair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64'));
  const patch = JSON.parse(Buffer.from(signedManifest(baseline, 7, 'https://example.com/screens.js', Buffer.from('program'), key).payload, 'base64'));
  const first = updateCatalog(null, patch, 'https://example.com/7.json', 'production');
  const nextBase = { ...patch, baseVersion: 'v2', hostSha256: 'c'.repeat(64), version: 1 };
  const next = updateCatalog(first, nextBase, 'https://example.com/v2.json', 'production');
  assert.equal(next.entries.length, 2);
  assert.equal(next.entries[0].version, 7);
  const envelope = signPayload(next, key, publicKey);
  assert.deepEqual(verifiedEnvelope(Buffer.from(JSON.stringify(envelope)), publicKey), next);
  assert.throws(() => verifiedEnvelope(Buffer.from(JSON.stringify({ ...envelope, payload: Buffer.from('{}').toString('base64') })), publicKey), /signature/);
  assert.throws(() => updateCatalog(first, { ...patch, version: 6 }, 'https://example.com/6.json', 'production'), /increase/);
  assert.throws(() => updateCatalog(first, nextBase, 'https://example.com/v2.json', 'beta'), /different app/);
  assert.throws(() => updateCatalog(first, { ...nextBase, appId: 'other.app' }, 'https://example.com/v2.json', 'production'), /different app/);
  assert.notEqual(routeId(baseline), routeId({ ...baseline, appId: 'other.app' }));
});

test('version reservations survive failed output and block reuse across output directories', () => temporary(root => {
  reserveVersion(root, baseline, 3, () => {});
  assert.throws(() => reserveVersion(root, baseline, 2, () => assert.fail('must not emit')), /exceed 3/);
  assert.throws(() => reserveVersion(root, baseline, 4, () => { throw new Error('disk failure'); }), /disk failure/);
  assert.throws(() => reserveVersion(root, baseline, 4, () => assert.fail('must not reuse')), /exceed 4/);
  reserveVersion(root, { ...baseline, hostSha256: 'c'.repeat(64) }, 1, () => {});
}));

test('installed-independent baseline, patch and explicit Git recovery with real Gradle/JAR tools', { timeout: 240000 }, () => temporary(async root => {
  const repository = fileURLToPath(new URL('../../', import.meta.url));
  mkdirSync(join(root, 'gradle/wrapper'), { recursive: true });
  cpSync(join(repository, 'gradle/wrapper'), join(root, 'gradle/wrapper'), { recursive: true });
  mkdirSync(join(root, 'app/assets'), { recursive: true });
  mkdirSync(join(root, 'screen'), { recursive: true });
  mkdirSync(join(root, '.keys'));
  writeFileSync(join(root, '.keys/private.key'), key);
  writeFileSync(join(root, 'public-key.txt'), keypair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64'));
  writeFileSync(join(root, '.gitignore'), '.patchrelay/\n.keys/\n.gradle/\nbuild/\n');
  writeFileSync(join(root, 'native.txt'), 'native v1');
  writeFileSync(join(root, 'screen/program.js'), 'globalThis.fixture = 1;');
  writeFileSync(join(root, 'app/assets/baseline-screen.js'), 'globalThis.fixture = 1;');
  writeFileSync(join(root, 'settings.gradle'), "rootProject.name='CliBuildFixture'\n");
  const runtime = { format: 1, appId: 'test.app', baseVersion: 'v1', screenApi: 2, screenIds: ['checkout'],
    nativeInputs: ['native.txt', 'app/assets/baseline-screen.js'], textExtensions: ['.txt', '.js'] };
  const settings = { format: 1, runtimeFile: 'runtime.json', repository: 'owner/repository', signingKey: '.keys/private.key', publicKeyFile: 'public-key.txt', screenInputs: ['screen'],
    compileTasks: ['compileProgram'], baseTasks: ['buildApp'], programFile: 'build/program.js', baselineAsset: 'app/assets/baseline-screen.js',
    apkFile: 'build/fixture.apk', buildConfigFile: 'build/BuildConfig.java', outputDir: '.patchrelay/releases' };
  writeFileSync(join(root, 'runtime.json'), JSON.stringify(runtime));
  writeFileSync(join(root, 'patchrelay.config.json'), JSON.stringify(settings));
  // Synthetic APK fixture exercises orchestration, not Android UI or APK validity.
  writeFileSync(join(root, 'build.gradle'), `
tasks.register('compileProgram') { doLast {
  file('build').mkdirs(); file('build/program.js').bytes = file('screen/program.js').bytes
} }
tasks.register('buildApp') { doLast {
  def hashes = new TreeMap<String,String>()
  ['native.txt','app/assets/baseline-screen.js'].each { path ->
    hashes[path] = java.security.MessageDigest.getInstance('SHA-256').digest(file(path).text.replace('\\r\\n','\\n').getBytes('UTF-8')).encodeHex().toString()
  }
  def hash = java.security.MessageDigest.getInstance('SHA-256').digest(groovy.json.JsonOutput.toJson(hashes).getBytes('UTF-8')).encodeHex().toString()
  file('build/BuildConfig.java').text = 'HOST_SHA256 = "' + hash + '"; APPLICATION_ID = "test.app"; BASE_VERSION = "v1";'
  file('build/fixture.apk').bytes = file('app/assets/baseline-screen.js').bytes
} }
`);
  await main(['baseline', '--name', 'original'], root);
  const record = join(root, '.patchrelay/releases/baselines/original/baseline.json');
  assert.equal(loadBaseline(record).appId, 'test.app');
  assert.ok(existsSync(join(dirname(record), 'baseline-sources.zip')));
  await assert.rejects(main(['baseline', '--name', 'original'], root), /Output exists/);
  command(root, 'git', ['init']);
  command(root, 'git', ['add', '.']);
  command(root, 'git', ['-c', 'user.name=CLI Test', '-c', 'user.email=cli@example.invalid', 'commit', '-m', 'original base']);
  const commit = command(root, 'git', ['rev-parse', 'HEAD'], true);
  writeFileSync(join(root, 'screen/program.js'), 'globalThis.fixture = 2;');
  await main(['patch', '--baseline', record, '--version', '1'], root);
  const savedBaseline = loadBaseline(record);
  const patchFile = join(root, `.patchrelay/releases/patches/${routeId(savedBaseline)}/1/patch.json`);
  const envelope = JSON.parse(readFileSync(patchFile));
  assert.ok(verify('sha256', Buffer.from(envelope.payload, 'base64'), keypair.publicKey, Buffer.from(envelope.signature, 'base64')));
  assert.equal(JSON.parse(Buffer.from(envelope.payload, 'base64')).artifactUrl, `https://github.com/owner/repository/releases/download/patch-${routeId(savedBaseline).slice(0, 24)}-1/screens.js`);
  await main(['catalog', '--patch', patchFile, '--catalog', 'new', '--channel', 'production', '--url', 'https://example.com/patch.json', '--out', '.patchrelay/catalog-test'], root);
  const catalog = verifiedEnvelope(readFileSync(join(root, '.patchrelay/catalog-test/catalog.json')), Buffer.from(keypair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64')));
  assert.equal(catalog.entries[0].hostSha256, savedBaseline.hostSha256);
  await main(['baseline', '--name', 'recovered', '--from', commit, '--apk', join(dirname(record), 'screen-base.apk')], root);
  assert.equal(readFileSync(join(root, 'screen/program.js'), 'utf8'), 'globalThis.fixture = 2;');
  assert.equal(command(root, 'git', ['rev-parse', 'HEAD'], true), commit);
  assert.equal(loadBaseline(join(root, '.patchrelay/releases/baselines/recovered/baseline.json')).recoveredFromCommit, commit);
  writeFileSync(join(root, 'wrong.apk'), 'different apk');
  await assert.rejects(main(['baseline', '--name', 'rejected', '--from', commit, '--apk', 'wrong.apk'], root), /Recovery rejected/);
  assert.equal(existsSync(join(root, '.patchrelay/releases/baselines/rejected')), false);
  assert.equal(readFileSync(join(root, 'screen/program.js'), 'utf8'), 'globalThis.fixture = 2;');
}));

