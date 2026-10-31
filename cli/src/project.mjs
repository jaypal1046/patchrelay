import { createHash, createPrivateKey, createPublicKey, constants, sign, verify } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, realpathSync, lstatSync, mkdirSync, openSync, closeSync, unlinkSync, writeFileSync, renameSync } from 'node:fs';
import { resolve, relative, dirname, isAbsolute, basename, join } from 'node:path';
import { spawnSync } from 'node:child_process';

export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
export const readJson = file => JSON.parse(readFileSync(file, 'utf8'));
export function baselineFiles(folder) {
  if (!existsSync(folder)) return [];
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    const path = inside(folder, entry.name);
    if (entry.isSymbolicLink()) return [];
    if (entry.isDirectory()) return baselineFiles(path);
    return entry.isFile() && entry.name === 'baseline.json' ? [path] : [];
  }).sort();
}
export const routeId = value => sha256(JSON.stringify([value.appId, value.baseVersion, value.hostSha256]));
// Match Python's existing sorted, ASCII-escaped JSON contract, including Unicode paths.
export const hostHash = files => sha256(JSON.stringify(Object.fromEntries(Object.entries(files)
  .sort(([a], [b]) => Buffer.compare(Buffer.from(a), Buffer.from(b)))))
  .replace(/[\u007f-\uffff]/g, char => `\\u${char.charCodeAt(0).toString(16).padStart(4, '0')}`));

export function inside(root, path) {
  const full = resolve(root, path);
  const child = relative(resolve(root), full);
  if (child === '..' || child.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`) || isAbsolute(child)) {
    throw new Error('Project path escapes the project directory.');
  }
  if (existsSync(full) && realpathSync(full) !== full) {
    const actual = relative(realpathSync(root), realpathSync(full));
    if (actual === '..' || actual.startsWith('..\\') || actual.startsWith('../') || isAbsolute(actual)) throw new Error('Project symlink escapes the project directory.');
  }
  return full;
}

export function fingerprint(root, paths, extensions) {
  const result = {};
  function visit(file) {
    inside(root, relative(root, file));
    const info = lstatSync(file);
    if (info.isSymbolicLink()) throw new Error('Declare real source paths, not symbolic links.');
    if (info.isDirectory()) {
      for (const name of readdirSync(file).sort()) visit(join(file, name));
    } else if (info.isFile()) {
      let data = readFileSync(file);
      if (extensions.some(suffix => basename(file).endsWith(suffix))) data = Buffer.from(data.toString('utf8').replaceAll('\r\n', '\n'));
      result[relative(root, file).replaceAll('\\', '/')] = sha256(data);
    }
  }
  for (const path of paths) visit(inside(root, path));
  return Object.fromEntries(Object.entries(result).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0));
}

export function checkNative(before, after) {
  const changed = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(name => before[name] !== after[name]);
  if (changed.length) throw new Error(`Native inputs changed; build a new base: ${changed.slice(0, 10).join(', ')}`);
}

export function emptyOutput(path) {
  mkdirSync(dirname(path), { recursive: true });
  mkdirSync(path); // Exclusive: never overwrite an existing release directory.
}

export function command(root, executable, args, capture = false) {
  const result = spawnSync(executable, args, { cwd: root, stdio: capture ? 'pipe' : 'inherit', encoding: 'utf8', shell: false });
  if (result.error) throw new Error(`Cannot run ${executable}: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`${executable} failed (${result.status}).${capture ? ` ${result.stderr?.trim() || ''}` : ''}`);
  return result.stdout?.trim();
}

export function jdkTool(name) {
  const binary = process.platform === 'win32' ? `${name}.exe` : name;
  return process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', binary) : binary;
}

export function gradle(root, tasks) {
  command(root, jdkTool('java'), ['-classpath', inside(root, 'gradle/wrapper/gradle-wrapper.jar'),
    'org.gradle.wrapper.GradleWrapperMain', ...tasks, '--console=plain', '--max-workers=2']);
}

export function loadBaseline(file) {
  const baseline = readJson(file);
  if (baseline.format !== 3 || !baseline.nativeFiles || !baseline.screenFiles || !Array.isArray(baseline.screenIds) ||
      !baseline.screenIds.length || !/^[a-f0-9]{64}$/.test(baseline.hostSha256) ||
      hostHash(baseline.nativeFiles) !== baseline.hostSha256) throw new Error('Invalid baseline compatibility record.');
  if (baseline.sourcesSha256) {
    const archive = join(dirname(file), 'baseline-sources.zip');
    if (!existsSync(archive) || sha256(readFileSync(archive)) !== baseline.sourcesSha256) throw new Error('Baseline source archive is missing or altered.');
  }
  return baseline;
}

export function validateUrl(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('Use an absolute HTTPS artifact URL.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error('Use HTTPS without URL credentials or a fragment.');
  return url.href;
}

export function signedManifest(baseline, version, artifactUrl, bytes, keyBytes, publicKeyBytes) {
  if (!Number.isSafeInteger(version) || version <= 0) throw new Error('Patch version must be a positive safe integer.');
  if (!bytes.length || bytes.length > 2 * 1024 * 1024) throw new Error('Patch must be between 1 byte and 2 MiB.');
  new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  return signPayload({ schema: 3, screenApi: baseline.screenApi, runtime: 'kotlin-screen-js',
    hostSha256: baseline.hostSha256, appId: baseline.appId, baseVersion: baseline.baseVersion, version,
    screenIds: baseline.screenIds, artifactUrl: validateUrl(artifactUrl), sha256: sha256(bytes) }, keyBytes, publicKeyBytes);
}

export function signPayload(value, keyBytes, publicKeyBytes) {
  const key = createPrivateKey({ key: Buffer.from(keyBytes.toString().trim(), 'base64'), format: 'der', type: 'pkcs8' });
  if (key.asymmetricKeyType !== 'rsa' || key.asymmetricKeyDetails.modulusLength < 3072) throw new Error('Use a PatchRelay RSA signing key of at least 3072 bits.');
  if (publicKeyBytes && !createPublicKey(key).export({ format: 'der', type: 'spki' }).equals(Buffer.from(publicKeyBytes.toString().trim(), 'base64'))) {
    throw new Error('Signing key does not match the configured app public key.');
  }
  const payload = Buffer.from(JSON.stringify(value));
  const envelope = { payload: payload.toString('base64'), signature: sign('sha256', payload, { key, padding: constants.RSA_PKCS1_PADDING }).toString('base64') };
  if (Buffer.byteLength(JSON.stringify(envelope)) > 64 * 1024) throw new Error('Signed metadata exceeds the SDK 64 KiB limit.');
  return envelope;
}

export function verifiedEnvelope(bytes, publicKeyBytes) {
  if (bytes.length > 64 * 1024) throw new Error('Signed metadata exceeds 64 KiB.');
  const envelope = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  if (typeof envelope.payload !== 'string' || typeof envelope.signature !== 'string') throw new Error('Invalid signed envelope.');
  const payload = Buffer.from(envelope.payload, 'base64');
  const key = createPublicKey({ key: Buffer.from(publicKeyBytes.toString().trim(), 'base64'), format: 'der', type: 'spki' });
  if (!verify('sha256', payload, key, Buffer.from(envelope.signature, 'base64'))) throw new Error('Metadata signature is invalid.');
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(payload));
}

export function updateCatalog(previous, manifest, manifestUrl, channel) {
  if (!/^[a-z][a-z0-9_-]{0,31}$/.test(channel)) throw new Error('Use a lowercase channel name, e.g. production or beta.');
  if (manifest.schema !== 3 || manifest.screenApi !== 2 || manifest.runtime !== 'kotlin-screen-js' || typeof manifest.appId !== 'string' ||
      !manifest.appId || typeof manifest.baseVersion !== 'string' || !manifest.baseVersion || manifest.baseVersion.length > 128 ||
      !/^[a-f0-9]{64}$/.test(manifest.hostSha256) || !Number.isSafeInteger(manifest.version) || manifest.version <= 0) throw new Error('Invalid patch route.');
  const catalog = previous || { schema: 1, kind: 'patchrelay-catalog', appId: manifest.appId, channel, entries: [] };
  if (catalog.schema !== 1 || catalog.kind !== 'patchrelay-catalog' || catalog.appId !== manifest.appId || catalog.channel !== channel ||
      !Array.isArray(catalog.entries) || catalog.entries.length > 64) throw new Error('Existing catalog belongs to a different app/channel or is invalid.');
  const seen = new Set();
  for (const entry of catalog.entries) {
    const route = JSON.stringify([entry.baseVersion, entry.hostSha256]);
    if (typeof entry.baseVersion !== 'string' || !entry.baseVersion || entry.baseVersion.length > 128 || !/^[a-f0-9]{64}$/.test(entry.hostSha256) ||
        !Number.isSafeInteger(entry.version) || entry.version <= 0 || seen.has(route)) throw new Error('Invalid or duplicate catalog route.');
    validateUrl(entry.manifestUrl);
    seen.add(route);
  }
  const old = catalog.entries.find(entry => entry.baseVersion === manifest.baseVersion && entry.hostSha256 === manifest.hostSha256);
  if (old && manifest.version <= old.version) throw new Error('Catalog patch versions must increase for each exact base.');
  const entries = catalog.entries.filter(entry => entry !== old);
  entries.push({ baseVersion: manifest.baseVersion, hostSha256: manifest.hostSha256, version: manifest.version, manifestUrl: validateUrl(manifestUrl) });
  if (entries.length > 64) throw new Error('Catalog has more than 64 base builds.');
  return { schema: 1, kind: 'patchrelay-catalog', appId: manifest.appId, channel, entries };
}

export function reserveVersion(root, baseline, version, writePatch) {
  const folder = inside(root, '.patchrelay/versions');
  mkdirSync(folder, { recursive: true });
  const file = join(folder, `${routeId(baseline)}.json`);
  let lock;
  try { lock = openSync(file + '.lock', 'wx'); }
  catch { throw new Error('Version record is locked. Another patch build may be running; inspect stale locks before removing them.'); }
  try {
    if (existsSync(file)) {
      const record = readJson(file);
      if (record.format !== 1 || !Number.isSafeInteger(record.highest) || record.highest <= 0) throw new Error('Invalid local patch version record. Restore its backup.');
      if (version <= record.highest) throw new Error(`Patch version must exceed ${record.highest} for this exact base.`);
    }
    const temporary = file + '.pending';
    // Reserve before emitting files: a failed write consumes a version rather than reusing a signature identity.
    writeFileSync(temporary, JSON.stringify({ format: 1, highest: version }) + '\n', { flag: 'wx', flush: true });
    renameSync(temporary, file);
    writePatch();
  } finally { closeSync(lock); unlinkSync(file + '.lock'); }
}
