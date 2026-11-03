import { existsSync, readFileSync, writeFileSync, mkdirSync, copyFileSync, cpSync, renameSync, mkdtempSync, rmSync, readdirSync, lstatSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline/promises';
import { fileURLToPath } from 'node:url';
import { sha256, readJson, hostHash, inside, fingerprint, checkNative, compareFiles, emptyOutput, command, jdkTool, gradle, loadBaseline, signedManifest, routeId, reserveVersion, verifiedEnvelope, updateCatalog, signPayload, validateUrl, baselineFiles, checkFunctions } from './project.mjs';

const configName = 'patchrelay.config.json';
const help = `PatchRelay — build and sign patches for prepared Android projects

  patchrelay init                 Configure this project once
  patchrelay baseline             Build and save a named base release
  patchrelay compare              Compare current files with a selected baseline
  patchrelay compare --diff       Also show actual source-line differences (requires Git)
  patchrelay patch [--github]     Build, sign, and publish assets to a GitHub Release
  patchrelay catalog              Update a signed app/channel catalog locally
  patchrelay baseline --from REF  Recover an explicitly selected old release

Options: --name NAME, --baseline PATH, --version NUMBER, --url HTTPS_URL, --github,
         --tag TAG, --out PATH, --from REF, --apk PATH
Catalog options: --patch PATH, --catalog PATH_OR_new, --channel NAME, --url MANIFEST_URL

Configured setup selects the current APK baseline and next version. Use --github to publish. No Python required.
Requires Node 22+, JDK 17, Android SDK and a prepared PatchRelay screen module.`;

function argumentsFor(args) {
  const [action = 'help', ...rest] = args;
  const options = {};
  const allowed = { init: [], baseline: ['name', 'out', 'from', 'apk'], compare: ['baseline', 'diff'], patch: ['baseline', 'version', 'url', 'tag', 'out', 'github'],
    catalog: ['patch', 'catalog', 'channel', 'url', 'out'] };
  if (['help', '--help', '-h', '--version'].includes(action)) return { action, options };
  if (!allowed[action]) throw new Error(`Unknown command: ${action}. Run patchrelay --help.`);
  if (rest.includes('--help')) return { action: 'help', options };
  for (let i = 0; i < rest.length; i++) {
    const key = rest[i].replace(/^--/, '');
    if (!rest[i].startsWith('--') || !allowed[action].includes(key) || Object.hasOwn(options, key)) {
      throw new Error(`Invalid option: ${rest[i]}. Run patchrelay --help.`);
    }
    if ((action === 'compare' && key === 'diff') || (action === 'patch' && key === 'github')) { options[key] = true; continue; }
    if (!rest[i + 1] || rest[i + 1].startsWith('--')) throw new Error(`Missing value: ${rest[i]}. Run patchrelay --help.`);
    options[key] = rest[++i];
  }
  if (options.apk && !options.from) throw new Error('--apk requires an explicit --from revision.');
  return { action, options };
}

function findRoot(start) {
  let path = resolve(start);
  while (true) {
    if (existsSync(join(path, configName)) || existsSync(join(path, 'runtime.json'))) return path;
    const parent = dirname(path);
    if (parent === path) throw new Error('Run in a prepared Android project containing runtime.json. See the CLI integration guide.');
    path = parent;
  }
}

function name(value) {
  if (!/^[A-Za-z0-9][A-Za-z0-9_.-]{0,119}$/.test(value)) throw new Error('Use a release name containing letters, numbers, dots, underscores or hyphens.');
  return value;
}

function project(root, fallback) {
  const settings = existsSync(join(root, configName)) ? readJson(join(root, configName)) : fallback;
  if (!settings || settings.format !== 1) throw new Error('Run patchrelay init first.');
  const runtime = readJson(inside(root, settings.runtimeFile));
  if (runtime.format !== 1 || runtime.screenApi !== 2 || !Array.isArray(runtime.nativeInputs) || !Array.isArray(runtime.screenIds) || !runtime.screenIds.length) throw new Error('Unsupported project runtime contract.');
  return { root, settings, runtime };
}

function nativeFiles(ctx) { return fingerprint(ctx.root, ctx.runtime.nativeInputs, ctx.runtime.textExtensions); }
function generatedFunctions(ctx) {
  if (!ctx.settings.functionContractFile) throw new Error('Configure functionContractFile from dev.patchrelay.functions and build a new native baseline.');
  const contract = readJson(inside(ctx.root, ctx.settings.functionContractFile));
  checkFunctions(contract, contract);
  return contract;
}
function screenFiles(ctx) { return fingerprint(ctx.root, ctx.settings.screenInputs, ctx.runtime.textExtensions); }
function saveJson(file, data) { writeFileSync(file, JSON.stringify(data, null, 2) + '\n', { flag: 'wx' }); }

async function publishGitHubRelease(root, repository, branch, tag, version, files) {
  let token = process.env.GITHUB_TOKEN;
  if (!token) {
    const credential = spawnSync('git', ['credential', 'fill'], { cwd: root, encoding: 'utf8', input: 'protocol=https\nhost=github.com\n\n' });
    token = credential.stdout?.split(/\r?\n/).find(line => line.startsWith('password='))?.slice(9);
  }
  if (!token) throw new Error('GitHub sign-in required. Sign in with Git Credential Manager or set GITHUB_TOKEN.');
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' };
  const api = `https://api.github.com/repos/${repository}/releases`;
  const request = async (url, options) => {
    const response = await fetch(url, { ...options, headers: { ...headers, ...options?.headers } });
    if (!response.ok) throw new Error(`GitHub API ${response.status}: ${(await response.text()).slice(0, 500)}`);
    return response.status === 204 ? null : response.json();
  };
  const release = await request(api, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tag_name: tag, target_commitish: branch, name: `PatchRelay patch ${version}`,
      body: `PatchRelay signed patch ${version}. Includes screens.js, patch.json, and the updated catalog.json.`, draft: true, prerelease: false }) });
  const uploadUrl = release.upload_url.split('{')[0];
  for (const file of files) {
    const asset = readFileSync(file);
    await request(`${uploadUrl}?name=${encodeURIComponent(file.split(/[\\/]/).pop())}`, {
      method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: asset });
  }
  await request(release.url, { method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ draft: false, make_latest: 'true' }) });
  return release.html_url;
}

function replacePatchOutput(folder, expectedFolder) {
  if (resolve(folder) !== resolve(expectedFolder)) throw new Error('Refusing to clean an unexpected patch output path.');
  if (existsSync(folder)) {
    if (lstatSync(folder).isSymbolicLink()) throw new Error('Patch output folder cannot be a symbolic link.');
    const entries = readdirSync(folder, { withFileTypes: true });
    if (entries.some(entry => entry.isSymbolicLink())) throw new Error('Patch output contains a symbolic link; move it before rebuilding.');
    const marker = '.patchrelay-managed-output';
    const marked = entries.some(entry => entry.name === marker);
    if (marked) {
      if (readFileSync(join(folder, marker), 'utf8') !== 'PatchRelay generated patch output\n' ||
          entries.some(entry => !entry.isFile() || ![marker, 'screens.js', 'patch.json', 'catalog.json'].includes(entry.name))) {
        throw new Error('Patch output contains unrecognized files; move them before rebuilding.');
      }
    } else {
      const oldTree = entries.every(route => route.isDirectory() && /^[a-f0-9]{64}$/.test(route.name) &&
        readdirSync(join(folder, route.name), { withFileTypes: true }).every(version => version.isDirectory() && /^\d+$/.test(version.name) &&
          readdirSync(join(folder, route.name, version.name), { withFileTypes: true }).every(file => file.isFile() && ['screens.js', 'patch.json', 'catalog.json'].includes(file.name))));
      if (!oldTree) throw new Error('Patch output is not a PatchRelay-generated folder; move it before rebuilding.');
    }
    rmSync(folder, { recursive: true });
  }
  mkdirSync(folder, { recursive: true });
  writeFileSync(join(folder, '.patchrelay-managed-output'), 'PatchRelay generated patch output\n', { flag: 'wx' });
}

function printComparison(label, changes) {
  console.log(`${label}: ${changes.added.length} added, ${changes.modified.length} modified, ${changes.removed.length} removed`);
  let count = 0;
  for (const [kind, paths] of Object.entries(changes)) {
    for (const path of paths) { console.log(`  ${kind.toUpperCase().padEnd(8)} ${path}`); count++; }
  }
  if (!count) console.log('  No changes');
  return count;
}

function printSourceDiff(ctx, file, baseline, changes) {
  if (!Object.values(changes).some(paths => paths.length)) return;
  if (!baseline.sourcesSha256) throw new Error('--diff requires a baseline with a verified source archive.');
  const archive = join(dirname(file), 'baseline-sources.zip');
  const folder = mkdtempSync(join(tmpdir(), 'patchrelay-compare-'));
  try {
    const sources = join(folder, 'sources');
    mkdirSync(sources);
    const entries = command(ctx.root, jdkTool('jar'), ['-J-Dfile.encoding=UTF-8', '--list', '--file', archive], true).split(/\r?\n/).filter(Boolean);
    for (const entry of entries) {
      if (entry.includes(':') || entry.startsWith('/') || entry.startsWith('\\') || entry.split(/[\\/]/).includes('..')) throw new Error('Unsafe baseline archive entry.');
      inside(sources, entry);
    }
    command(sources, jdkTool('jar'), ['--extract', '--file', archive]);
    const empty = join(folder, '.patchrelay-empty-file');
    writeFileSync(empty, '');
    for (const [kind, paths] of Object.entries(changes)) for (const path of paths) {
      const oldFile = kind === 'added' ? empty : inside(sources, path);
      const newFile = kind === 'removed' ? empty : inside(ctx.root, path);
      if (!existsSync(oldFile) || !existsSync(newFile)) throw new Error(`Missing comparison source: ${path}`);
      const result = spawnSync('git', ['diff', '--no-index', '--no-ext-diff', '--no-textconv', '--no-color', '--', oldFile, newFile], {cwd: folder, encoding: 'utf8', shell: false, maxBuffer: 16 * 1024 * 1024});
      if (result.error || ![0, 1].includes(result.status)) throw new Error(`Cannot compare ${path}; --diff requires Git. ${result.error?.message || result.stderr || ''}`);
      console.log(`\n${kind.toUpperCase()} ${path}`);
      console.log(result.stdout.replace(/^diff --git .*$/m, `diff --git baseline/${path} current/${path}`)
        .replace(/^--- .*$/m, kind === 'added' ? '--- /dev/null' : `--- baseline/${path}`)
        .replace(/^\+\+\+ .*$/m, kind === 'removed' ? '+++ /dev/null' : `+++ current/${path}`).trimEnd());
    }
  } finally {
    if (dirname(folder) !== tmpdir() || !folder.startsWith(join(tmpdir(), 'patchrelay-compare-'))) throw new Error('Unsafe comparison cleanup target');
    rmSync(folder, {recursive: true, force: true});
  }
}

export function verifyBuild(ctx, expected) {
  const source = readFileSync(inside(ctx.root, ctx.settings.buildConfigFile), 'utf8');
  for (const [field, value] of Object.entries({ HOST_SHA256: expected, APPLICATION_ID: ctx.runtime.appId, BASE_VERSION: ctx.runtime.baseVersion })) {
    let text = source;
    if (field === 'HOST_SHA256') {
      const generated = source.match(/\bHOST_SHA256 = ([\w.]+\.PatchRelayBuild)\.HOST_SHA256;/);
      if (generated) {
        const [module, tail] = ctx.settings.buildConfigFile.replaceAll('\\', '/').split('/build/generated/source/buildConfig/');
        const variant = tail?.split('/')[0];
        if (!variant) throw new Error('Configure the standard generated BuildConfig path for Gradle baseline integration.');
        const qualified = generated[1].replaceAll('.', '/');
        const candidates = [`${module}/build/generated/java/preparePatchRelay${variant[0].toUpperCase() + variant.slice(1)}/${qualified}.java`, `${module}/build/generated/patchrelay/${variant}/${qualified}.java`];
        const file = candidates.map(path => inside(ctx.root, path)).find(existsSync);
        if (!file) throw new Error('Missing generated PatchRelay fingerprint source. Build the selected native variant first.');
        text = readFileSync(file, 'utf8');
      }
    }
    const match = text.match(new RegExp(`\\b${field} = (?:new String\\()?("(?:[^"\\\\]|\\\\.)*")`));
    if (!match || JSON.parse(match[1]) !== value) throw new Error(`Built APK ${field} does not match the release contract. Check host Gradle integration.`);
  }
}

function buildBaseline(ctx, out) {
  const { root, settings, runtime } = ctx;
  emptyOutput(out);
  gradle(root, settings.compileTasks);
  const program = readFileSync(inside(root, settings.programFile));
  if (!program.length || program.length > 2 * 1024 * 1024) throw new Error('Compiled screen program must be 1 byte–2 MiB.');
  new TextDecoder('utf-8', { fatal: true }).decode(program);
  // The bundled program is a native-build input; include it before hashing/building.
  copyFileSync(inside(root, settings.programFile), inside(root, settings.baselineAsset));
  const before = nativeFiles(ctx);
  const sources = screenFiles(ctx);
  gradle(root, settings.baseTasks);
  checkNative(before, nativeFiles(ctx));
  checkNative(sources, screenFiles(ctx));
  verifyBuild(ctx, hostHash(before));
  const apk = readFileSync(inside(root, settings.apkFile));
  writeFileSync(join(out, 'screen-base.apk'), apk, { flag: 'wx' });
  writeFileSync(join(out, 'baseline-screen.js'), program, { flag: 'wx' });
  const staging = inside(root, `.patchrelay/work/${randomUUID()}/sources`);
  mkdirSync(staging, { recursive: true });
  for (const path of [...new Set([...Object.keys(before), ...Object.keys(sources)])]) {
    if (path.split('/').some(part => ['.keys', 'local.properties', '.env'].includes(part)) || /\.(key|pem|p12|jks|keystore)$/i.test(path) ||
        resolve(root, path) === resolve(root, settings.signingKey)) throw new Error('A private credential file is declared as a baseline input. Remove it from native/screen inputs.');
    const target = inside(staging, path);
    mkdirSync(dirname(target), { recursive: true });
    copyFileSync(inside(root, path), target);
  }
  checkNative(before, fingerprint(staging, Object.keys(before), runtime.textExtensions));
  checkNative(sources, fingerprint(staging, Object.keys(sources), runtime.textExtensions));
  const archive = join(out, 'baseline-sources.zip');
  command(root, jdkTool('jar'), ['--create', '--file', archive, '-C', staging, '.']);
  const baseline = { format: 3, appId: runtime.appId, baseVersion: runtime.baseVersion, screenApi: runtime.screenApi,
    functionContract: generatedFunctions(ctx),
    hostSha256: hostHash(before), screenIds: runtime.screenIds, nativeFiles: before, screenFiles: sources,
    apkSha256: sha256(apk), programSha256: sha256(program), sourcesSha256: sha256(readFileSync(archive)) };
  saveJson(join(out, 'baseline.json'), baseline);
  return baseline;
}

async function init(root, ask) {
  if (existsSync(join(root, configName))) throw new Error('Project is already configured. Edit patchrelay.config.json to change settings.');
  const runtime = readJson(join(root, 'runtime.json'));
  const app = await ask('Android app module', existsSync(join(root, 'demo')) ? 'demo' : 'app');
  const screens = await ask('Prepared Kotlin screen module', 'screens');
  if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(app) || !/^[A-Za-z][A-Za-z0-9_-]*$/.test(screens)) throw new Error('Use simple Gradle module names.');
  const repository = await ask('Patch GitHub repository (owner/repo), or "https" for another host');
  if (repository !== 'https' && !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) throw new Error('Use owner/repo, or https.');
  const patchBaseUrl = repository === 'https'
    ? validateUrl((await ask('HTTPS base URL where versioned patch files will be hosted')).replace(/\/+$/, ''))
    : null;
  const signingKey = await ask('Private signing-key file location (not key contents)', '.keys/patch-signing.key');
  const catalogUrl = validateUrl(await ask('Stable HTTPS URL for this app\'s signed catalog'));
  const channel = await ask('Update channel', 'production');
  if (!/^[a-z][a-z0-9_-]{0,31}$/.test(channel)) throw new Error('Use a lowercase channel name.');
  const patchOutputDir = `${screens}/build/patchrelay`;
  const defaultCatalogFile = `${patchOutputDir}/catalog.json`;
  const previousCatalog = await ask('Existing local signed catalog path, or "new" for first catalog', 'new');
  const catalogFile = previousCatalog === 'new' ? defaultCatalogFile : previousCatalog;
  if (!catalogFile.endsWith('.json')) throw new Error('Catalog path must end with .json, or use "new" for the first catalog.');
  if (previousCatalog !== 'new' && !existsSync(inside(root, catalogFile))) throw new Error(`Signed catalog file not found: ${catalogFile}. Download the current catalog or choose "new" only for the first release.`);
  if (previousCatalog === 'new') inside(root, catalogFile);
  const inputFile = async (label, fallback, suffix) => {
    const path = await ask(label, fallback);
    if (typeof path !== 'string' || !path.trim() || !path.endsWith(suffix)) throw new Error(`${label} requires a file path${suffix ? ` ending with ${suffix}` : ''}.`);
    inside(root, path);
    return path;
  };
  const programFile = await inputFile('Compiled Kotlin/JS program file', `${screens}/build/compileSync/js/main/productionExecutable/kotlin/${screens === 'screens' ? 'PatchRelayScreens' : screens}.js`, '.js');
  const apkFile = await inputFile('Release APK file', `${app}/build/outputs/apk/release/${app}-release.apk`, '.apk');
  const buildConfigFile = await inputFile('Generated release BuildConfig file', `${app}/build/generated/source/buildConfig/release/${runtime.appId.replaceAll('.', '/')}/BuildConfig.java`, '.java');
  const publicKeyFile = await inputFile('Pinned public-key file', `${app}/patch-public-key.txt`, '');
  const settings = { format: 1, runtimeFile: 'runtime.json', repository: repository === 'https' ? null : repository, patchBaseUrl, signingKey,
    publicKeyFile, catalogUrl, catalogFile, channel,
    baselinePointerFile: `${app}/build/outputs/patchrelay/release/apk-record.json`, patchOutputDir,
    functionContractFile: `${screens}/build/generated/patchrelay/functions.json`,
    screenInputs: [`${screens}/src/commonMain`], compileTasks: [`:${screens}:jvmTest`, `:${screens}:compileProductionExecutableKotlinJs`],
    baseTasks: [`:${app}:assembleRelease`], programFile,
    baselineAsset: `${app}/src/main/assets/baseline-screen.js`, apkFile, buildConfigFile,
    outputDir: '.patchrelay/releases' };
  const pluginSource = existsSync(fileURLToPath(new URL('../.patchrelay-tools/build.gradle', import.meta.url)))
    ? fileURLToPath(new URL('../.patchrelay-tools/', import.meta.url))
    : fileURLToPath(new URL('../../.patchrelay-tools/', import.meta.url));
  const pluginClass = 'src/main/groovy/dev/patchrelay/gradle/PatchRelayPlugin.groovy';
  const pluginFolder = '.patchrelay-tools';
  if (existsSync(join(root, pluginFolder)) && !existsSync(join(root, pluginFolder, pluginClass))) {
    throw new Error('Plugin destination already contains unrelated files. Preserve them and choose a clean integration directory.');
  }
  if (!existsSync(join(root, pluginFolder))) cpSync(pluginSource, join(root, pluginFolder), {
    recursive: true, errorOnExist: true, force: false,
    filter: path => !path.endsWith('/build') && !path.endsWith('\\build') && !path.endsWith('/.gradle') && !path.endsWith('\\.gradle')
  });
  if (Array.isArray(runtime.nativeInputs)) {
    for (const path of [`${pluginFolder}/src/main`, `${pluginFolder}/build.gradle`, `${pluginFolder}/settings.gradle`]) {
      if (!runtime.nativeInputs.includes(path)) runtime.nativeInputs.push(path);
    }
    writeFileSync(join(root, 'runtime.json'), JSON.stringify(runtime, null, 2) + '\n');
  }
  saveJson(join(root, configName), settings);
  const ignoreFile = join(root, '.gitignore');
  let ignored = existsSync(ignoreFile) ? readFileSync(ignoreFile, 'utf8') : '';
  for (const rule of ['.patchrelay/', '.keys/', 'local.properties']) {
    if (!ignored.split(/\r?\n/).includes(rule)) ignored += `${ignored.endsWith('\n') || !ignored ? '' : '\n'}${rule}\n`;
  }
  writeFileSync(ignoreFile, ignored);
  const localFile = join(root, 'local.properties');
  const local = existsSync(localFile) ? readFileSync(localFile, 'utf8') : '';
  writeFileSync(localFile, local + (local.endsWith('\n') || !local ? '' : '\n') + `patchrelay.catalogUrl=${catalogUrl}\npatchrelay.channel=${channel}\n`);
  console.log('Saved patchrelay.config.json. Review module paths before building. Keep private keys and .patchrelay/ out of Git.');
  console.log(`Kotlin sources: ${screens}/src/commonMain (all files recursively; no individual file list needed).\nGenerated upload names: screens.js (program), patch.json (signed manifest), catalog.json (signed discovery catalog).\nKeep APKs, baseline backups, source archives and private signing keys off patch hosting. Patch and catalog commands print exact upload paths.`);
  console.log(`Shared module: apply id("dev.patchrelay.functions"), and add the shared JVM module as an Android dependency. Use generated PatchRelayFunctions hooks.\nGradle hook: add includeBuild("${pluginFolder}") inside settings pluginManagement, and id("dev.patchrelay.baseline") in the app plugins block. Commit the plugin sources/config with your native release.`);
}

async function recover(ctx, ref, original, out) {
  if (!ref || ref.startsWith('-')) throw new Error('Choose an explicit commit, tag or branch.');
  const expected = sha256(readFileSync(original));
  const commit = command(ctx.root, 'git', ['rev-parse', '--verify', '--end-of-options', `${ref}^{commit}`], true);
  const work = inside(ctx.root, `.patchrelay/recovery/${randomUUID()}`);
  mkdirSync(work, { recursive: true });
  const checkout = join(work, 'checkout');
  command(ctx.root, 'git', ['worktree', 'add', '--detach', checkout, commit]);
  console.log(`Recovery checkout: ${checkout}`);
  const candidate = join(work, 'candidate');
  const baseline = buildBaseline(project(checkout, ctx.settings), candidate);
  if (baseline.apkSha256 !== expected) {
    const file = join(candidate, 'baseline.json');
    // Failed candidates must not look like accepted release records.
    renameSync(file, join(candidate, 'unverified-baseline.json'));
    throw new Error('Rebuilt APK differs from the supplied original APK. Recovery rejected; current checkout is unchanged.');
  }
  baseline.recoveredFromCommit = commit;
  writeFileSync(join(candidate, 'baseline.json'), JSON.stringify(baseline, null, 2) + '\n');
  emptyOutput(out);
  cpSync(candidate, out, { recursive: true, force: false, errorOnExist: true });
}

export async function main(args = process.argv.slice(2), cwd = process.cwd(), input) {
  const { action, options } = argumentsFor(args);
  if (action === '--version') { console.log('0.1.0-alpha04'); return; }
  if (['help', '--help', '-h'].includes(action)) { console.log(help); return; }
  let terminal;
  const ask = input || (async (question, fallback) => {
    if (!process.stdin.isTTY) throw new Error(`Missing value: ${question}. Supply flags or run in an interactive terminal.`);
    terminal ??= createInterface({ input: process.stdin, output: process.stdout });
    const answer = (await terminal.question(`${question}${fallback ? ` [${fallback}]` : ''}: `)).trim() || fallback;
    if (!answer) throw new Error(`${question} is required.`);
    return answer;
  });
  try {
    const root = findRoot(cwd);
    if (action === 'init') { await init(root, ask); return; }
    const ctx = project(root);
    if (action === 'baseline') {
      const release = name(options.name || await ask('New baseline record name'));
      const out = options.out ? resolve(root, options.out) : inside(root, `${ctx.settings.outputDir}/baselines/${release}`);
      if (existsSync(out)) throw new Error('Output exists. Choose a new baseline name or output directory.');
      if (options.from) {
        const apk = resolve(root, options.apk || await ask('Original released APK file'));
        await recover(ctx, options.from, apk, out);
      } else buildBaseline(ctx, out);
      console.log(`Baseline saved: ${out}\nBack up this entire directory privately.`);
    } else if (action === 'catalog') {
      const publicKey = readFileSync(inside(root, ctx.settings.publicKeyFile));
      const patchFile = resolve(root, options.patch || await ask('Signed patch.json file path'));
      const manifest = verifiedEnvelope(readFileSync(patchFile), publicKey);
      if (manifest.appId !== ctx.runtime.appId) throw new Error('Selected patch belongs to another app.');
      const bytes = readFileSync(join(dirname(patchFile), 'screens.js'));
      if (sha256(bytes) !== manifest.sha256) throw new Error('Patch artifact does not match its signed manifest.');
      const previousFile = options.catalog || await ask('Existing signed catalog path, or "new" for the first catalog');
      const previous = previousFile === 'new' ? null : verifiedEnvelope(readFileSync(resolve(root, previousFile)), publicKey);
      const channel = options.channel || await ask('Channel', 'production');
      const url = validateUrl(options.url || await ask('Final HTTPS URL of this patch.json'));
      const catalog = updateCatalog(previous, manifest, url, channel);
      const envelope = signPayload(catalog, readFileSync(resolve(root, ctx.settings.signingKey)), publicKey);
      const out = options.out ? resolve(root, options.out) : inside(root, `${ctx.settings.outputDir}/catalogs/${randomUUID()}`);
      emptyOutput(out);
      saveJson(join(out, 'catalog.json'), envelope);
      console.log(`Signed catalog saved: ${join(out, 'catalog.json')}\nApp: ${catalog.appId}; channel: ${channel}; base builds: ${catalog.entries.length}\nNothing uploaded. Serve this catalog at the app's stable catalog URL after uploading its patch files.`);
      console.log(`Upload in this order:\n1. ${join(dirname(patchFile), 'screens.js')} -> ${manifest.artifactUrl}\n2. ${patchFile} -> ${url}\n3. ${join(out, 'catalog.json')} -> ${ctx.settings.catalogUrl || 'the stable catalog URL configured in the native app'}\nKeep older hosted patch files: existing catalog routes may still reference them.`);
    } else {
      const baselineDir = inside(root, `${ctx.settings.outputDir}/baselines`);
      if (!options.baseline && !ctx.settings.baselinePointerFile && existsSync(baselineDir)) {
        console.log('Available records (choose explicitly):\n' + baselineFiles(baselineDir).map(file => `  ${file}`).join('\n'));
      }
      let selectedBaseline = options.baseline;
      if (!selectedBaseline && ctx.settings.baselinePointerFile) {
        const pointerPath = inside(root, ctx.settings.baselinePointerFile);
        if (!existsSync(pointerPath)) throw new Error(`No current APK baseline is recorded. Build the configured release APK first; expected ${pointerPath}.`);
        const pointer = readJson(pointerPath);
        if (typeof pointer.baselineFile !== 'string') throw new Error(`Invalid baseline pointer: ${pointerPath}`);
        selectedBaseline = pointer.baselineFile;
      }
      const file = resolve(root, selectedBaseline || await ask('Enter one of the baseline.json paths listed above'));
      if (!existsSync(file)) throw new Error(`Baseline JSON not found: ${file}. Choose a listed baseline.json path; "new" is only valid for the first catalog.`);
      const baseline = loadBaseline(file);
      if (baseline.appId !== ctx.runtime.appId || baseline.baseVersion !== ctx.runtime.baseVersion || baseline.screenApi !== ctx.runtime.screenApi ||
          JSON.stringify(baseline.screenIds) !== JSON.stringify(ctx.runtime.screenIds)) throw new Error('Selected baseline belongs to a different app/base/screen contract.');
      checkFunctions(baseline.functionContract, baseline.functionContract);
      const missingNative = ctx.runtime.nativeInputs.filter(path => !existsSync(inside(root, path)));
      const missingScreens = ctx.settings.screenInputs.filter(path => !existsSync(inside(root, path)));
      const currentNative = action === 'compare' ? fingerprint(root, ctx.runtime.nativeInputs.filter(path => !missingNative.includes(path)), ctx.runtime.textExtensions) : nativeFiles(ctx);
      const currentScreens = action === 'compare' ? fingerprint(root, ctx.settings.screenInputs.filter(path => !missingScreens.includes(path)), ctx.runtime.textExtensions) : screenFiles(ctx);
      console.log(`Baseline: ${file}\nApp: ${baseline.appId}; base: ${baseline.baseVersion}\nNative fingerprint: ${baseline.hostSha256}`);
      const nativeDiff = compareFiles(baseline.nativeFiles, currentNative);
      const screenDiff = compareFiles(baseline.screenFiles, currentScreens);
      const nativeChanges = printComparison('Native/build files (new APK required when changed)', nativeDiff);
      const screenChanges = printComparison('Supported Kotlin/screen files', screenDiff);
      if (action === 'compare') {
        for (const path of [...missingNative, ...missingScreens]) console.log(`  MISSING configured input: ${path}`);
        console.log(nativeChanges ? 'New APK baseline required before patching.' : missingNative.length || missingScreens.length ? 'Fix missing configured inputs before patching.' : 'Native inputs match. Function/signature compatibility is checked during patch compilation.');
        console.log('Comparison uses configured baseline inputs, not every repository file. No build, signing or version reservation performed.');
        if (options.diff) {
          printSourceDiff(ctx, file, baseline, compareFiles({...baseline.nativeFiles, ...baseline.screenFiles}, {...currentNative, ...currentScreens}));
        }
        return;
      }
      checkNative(baseline.nativeFiles, currentNative);
      if (!screenChanges) throw new Error('No supported screen changes found.');
      const configuredCatalog = ctx.settings.catalogFile && ctx.settings.catalogUrl;
      const git = (...args) => {
        const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
        if (result.status !== 0) throw new Error((result.stderr || result.stdout || 'Git command failed').trim());
        return result.stdout.trim();
      };
      const branch = options.github ? git('branch', '--show-current') : null;
      if (options.github && !branch) throw new Error('--github requires a checked-out branch.');
      let githubRepository;
      if (options.github) {
        const remote = git('remote', 'get-url', 'origin');
        const match = remote.match(/github\.com[:/]([^/]+\/[^/]+?)(?:\.git)?$/i);
        if (!match) throw new Error('--github needs an origin remote pointing to GitHub.');
        if (ctx.settings.repository && ctx.settings.repository.toLowerCase() !== match[1].toLowerCase()) {
          throw new Error(`Configured repository ${ctx.settings.repository} does not match origin ${match[1]}.`);
        }
        githubRepository = match[1];
        if (!ctx.settings.catalogFile || options.url || options.out) throw new Error('--github needs a configured catalog and default output/URL settings.');
      }
      const releaseBase = options.github ? `https://github.com/${githubRepository}/releases/download` : null;
      if (options.github) {
        ctx.settings.patchBaseUrl = releaseBase;
        ctx.settings.catalogUrl = `https://github.com/${githubRepository}/releases/latest/download/catalog.json`;
      }
      const publicKey = readFileSync(inside(root, ctx.settings.publicKeyFile));
      const catalogFile = configuredCatalog ? inside(root, ctx.settings.catalogFile) : null;
      const previousCatalog = catalogFile && existsSync(catalogFile) ? verifiedEnvelope(readFileSync(catalogFile), publicKey) : null;
      const catalogChannel = ctx.settings.channel || 'production';
      const priorRoute = previousCatalog?.entries?.find(entry => entry.baseVersion === baseline.baseVersion && entry.hostSha256 === baseline.hostSha256);
      const versionRecord = inside(root, `.patchrelay/versions/${routeId(baseline)}.json`);
      const versionData = existsSync(versionRecord) ? readJson(versionRecord) : null;
      if (versionData && (versionData.format !== 1 || !Number.isSafeInteger(versionData.highest) || versionData.highest <= 0)) throw new Error('Invalid local patch version record. Restore its backup.');
      const nextVersion = Math.max(versionData?.highest || 0, priorRoute?.version || 0) + 1;
      const version = Number(options.version || (configuredCatalog ? nextVersion : await ask('Patch version (positive integer)')));
      if (!Number.isSafeInteger(version) || version <= 0) throw new Error('Patch version must be a positive safe integer.');
      const route = routeId(baseline);
      const tag = name(options.tag || `patch-${route.slice(0, 24)}-${version}`);
      const patchBase = ctx.settings.patchBaseUrl?.replace(/\/+$/, '');
      const url = options.github
        ? `${releaseBase}/${tag}/screens.js`
        : options.url || (ctx.settings.repository
        ? `https://github.com/${ctx.settings.repository}/releases/download/${tag}/screens.js`
        : patchBase ? `${patchBase}/${tag}/screens.js` : await ask('Final HTTPS URL for screens.js'));
      const patchOutputDir = ctx.settings.patchOutputDir;
      const out = options.out ? resolve(root, options.out) : inside(root, patchOutputDir
        ? `${patchOutputDir}/patches`
        : `${ctx.settings.outputDir}/patches/${route}/${version}`);
      if (options.out && existsSync(out)) throw new Error(`Output already exists: ${out}. Choose a new output directory.`);
      gradle(root, ctx.settings.compileTasks);
      checkNative(currentNative, nativeFiles(ctx));
      checkNative(currentScreens, screenFiles(ctx));
      checkFunctions(baseline.functionContract, generatedFunctions(ctx));
      const bytes = readFileSync(inside(root, ctx.settings.programFile));
      if (sha256(bytes) === baseline.programSha256) throw new Error('Compiled program is unchanged.');
      if (!ctx.settings.publicKeyFile) throw new Error('Configure publicKeyFile to match the key pinned in your app.');
      const envelope = signedManifest(baseline, version, url, bytes, readFileSync(resolve(root, ctx.settings.signingKey)),
        publicKey);
      const patchManifestUrl = url.replace(/\/screens\.js$/, '/patch.json');
      const updatedCatalog = configuredCatalog
        ? signPayload(updateCatalog(previousCatalog, verifiedEnvelope(Buffer.from(JSON.stringify(envelope)), publicKey), patchManifestUrl, catalogChannel),
          readFileSync(resolve(root, ctx.settings.signingKey)), publicKey)
        : null;
      if (updatedCatalog && !url.endsWith('/screens.js')) throw new Error('Configured catalog publishing requires a screens.js URL so the signed patch.json URL can be derived.');
      reserveVersion(root, baseline, version, () => {
        if (!options.out && patchOutputDir) replacePatchOutput(out, inside(root, `${patchOutputDir}/patches`));
        else emptyOutput(out);
        writeFileSync(join(out, 'screens.js'), bytes, { flag: 'wx' });
        saveJson(join(out, 'patch.json'), envelope);
        if (updatedCatalog) {
          saveJson(join(out, 'catalog.json'), updatedCatalog);
          mkdirSync(dirname(catalogFile), { recursive: true });
          const temporaryCatalog = `${catalogFile}.${randomUUID()}.tmp`;
          writeFileSync(temporaryCatalog, JSON.stringify(updatedCatalog, null, 2) + '\n', { flag: 'wx', flush: true });
          renameSync(temporaryCatalog, catalogFile);
        }
      });
      let releaseUrl;
      if (options.github) {
        const localFile = join(root, 'local.properties');
        const local = existsSync(localFile) ? readFileSync(localFile, 'utf8') : '';
        const properties = local.split(/\r?\n/).filter(line => !/^patchrelay\.(catalogUrl|channel)=/.test(line));
        properties.push(`patchrelay.catalogUrl=${ctx.settings.catalogUrl}`, `patchrelay.channel=${catalogChannel}`);
        writeFileSync(localFile, properties.filter(Boolean).join('\n') + '\n');
        releaseUrl = await publishGitHubRelease(root, githubRepository, branch, tag, version,
          ['screens.js', 'patch.json', 'catalog.json'].map(file => join(out, file)));
      }
      console.log(`Signed patch saved: ${out}${options.github ? '' : `\nNothing uploaded. Target release tag: ${tag}`}`);
      console.log(options.github
        ? `Patch assets published: ${releaseUrl}\nApp catalog: ${ctx.settings.catalogUrl}`
        : updatedCatalog
        ? `Upload these files in order:\n1. ${join(out, 'screens.js')} -> ${url}\n2. ${join(out, 'patch.json')} -> ${patchManifestUrl}\n3. ${join(out, 'catalog.json')} -> ${ctx.settings.catalogUrl}\nLocal latest catalog saved: ${catalogFile}`
        : `Patch upload files:\n${join(out, 'screens.js')} -> ${url}\n${join(out, 'patch.json')} -> the manifest HTTPS URL you will supply to patchrelay catalog\nNext: patchrelay catalog creates catalog.json and prints the complete upload checklist.`);
    }
  } finally { terminal?.close(); }
}
