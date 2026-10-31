import { existsSync, readFileSync, writeFileSync, mkdirSync, copyFileSync, cpSync, renameSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline/promises';
import { fileURLToPath } from 'node:url';
import { sha256, readJson, hostHash, inside, fingerprint, checkNative, emptyOutput, command, jdkTool, gradle, loadBaseline, signedManifest, routeId, reserveVersion, verifiedEnvelope, updateCatalog, signPayload, validateUrl, baselineFiles } from './project.mjs';

const configName = 'patchrelay.config.json';
const help = `PatchRelay — build and sign patches for prepared Android projects

  patchrelay init                 Configure this project once
  patchrelay baseline             Build and save a named base release
  patchrelay patch                Select a baseline, build and sign a patch
  patchrelay catalog              Update a signed app/channel catalog locally
  patchrelay baseline --from REF  Recover an explicitly selected old release

Options: --name NAME, --baseline PATH, --version NUMBER, --url HTTPS_URL,
         --tag TAG, --out PATH, --from REF, --apk PATH
Catalog options: --patch PATH, --catalog PATH_OR_new, --channel NAME, --url MANIFEST_URL

No automatic baseline selection. No uploads. No Python required.
Requires Node 22+, JDK 17, Android SDK and a prepared PatchRelay screen module.`;

function argumentsFor(args) {
  const [action = 'help', ...rest] = args;
  const options = {};
  const allowed = { init: [], baseline: ['name', 'out', 'from', 'apk'], patch: ['baseline', 'version', 'url', 'tag', 'out'],
    catalog: ['patch', 'catalog', 'channel', 'url', 'out'] };
  if (['help', '--help', '-h', '--version'].includes(action)) return { action, options };
  if (!allowed[action]) throw new Error(`Unknown command: ${action}. Run patchrelay --help.`);
  if (rest.includes('--help')) return { action: 'help', options };
  for (let i = 0; i < rest.length; i += 2) {
    const key = rest[i].replace(/^--/, '');
    if (!rest[i].startsWith('--') || !allowed[action].includes(key) || !rest[i + 1] || rest[i + 1].startsWith('--') || Object.hasOwn(options, key)) {
      throw new Error(`Invalid option: ${rest[i]}. Run patchrelay --help.`);
    }
    options[key] = rest[i + 1];
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
function screenFiles(ctx) { return fingerprint(ctx.root, ctx.settings.screenInputs, ctx.runtime.textExtensions); }
function saveJson(file, data) { writeFileSync(file, JSON.stringify(data, null, 2) + '\n', { flag: 'wx' }); }

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
        text = readFileSync(inside(ctx.root, `${module}/build/generated/patchrelay/${variant}/${generated[1].replaceAll('.', '/')}.java`), 'utf8');
      }
    }
    const match = text.match(new RegExp(`\\b${field} = ("(?:[^"\\\\]|\\\\.)*")`));
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
  const signingKey = await ask('Private signing-key file location (not key contents)', '.keys/patch-signing.key');
  const catalogUrl = validateUrl(await ask('Stable HTTPS URL for this app\'s signed catalog'));
  const channel = await ask('Update channel', 'production');
  if (!/^[a-z][a-z0-9_-]{0,31}$/.test(channel)) throw new Error('Use a lowercase channel name.');
  const settings = { format: 1, runtimeFile: 'runtime.json', repository: repository === 'https' ? null : repository, signingKey,
    publicKeyFile: `${app}/patch-public-key.txt`,
    screenInputs: [`${screens}/src/commonMain`], compileTasks: [`:${screens}:jvmTest`, `:${screens}:compileProductionExecutableKotlinJs`],
    baseTasks: [`:${app}:assembleRelease`], programFile: `${screens}/build/compileSync/js/main/productionExecutable/kotlin/${screens === 'screens' ? 'PatchRelayScreens' : screens}.js`,
    baselineAsset: `${app}/src/main/assets/baseline-screen.js`, apkFile: `${app}/build/outputs/apk/release/${app}-release.apk`,
    buildConfigFile: `${app}/build/generated/source/buildConfig/release/${runtime.appId.replaceAll('.', '/')}/BuildConfig.java`,
    outputDir: '.patchrelay/releases' };
  const pluginSource = existsSync(fileURLToPath(new URL('../gradle-plugin/build.gradle', import.meta.url)))
    ? fileURLToPath(new URL('../gradle-plugin/', import.meta.url))
    : fileURLToPath(new URL('../../gradle-plugin/', import.meta.url));
  const pluginClass = 'src/main/groovy/dev/patchrelay/gradle/PatchRelayPlugin.groovy';
  const pluginFolder = existsSync(join(root, 'gradle-plugin', pluginClass)) ? 'gradle-plugin' : 'patchrelay-gradle-plugin';
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
  console.log(`Gradle hook: add includeBuild("${pluginFolder}") inside settings pluginManagement, and id("dev.patchrelay.baseline") in the app plugins block. Commit the plugin sources/config with your native release.`);
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
    } else {
      const baselineDir = inside(root, `${ctx.settings.outputDir}/baselines`);
      if (!options.baseline && existsSync(baselineDir)) {
        console.log('Available records (choose explicitly):\n' + baselineFiles(baselineDir).map(file => `  ${file}`).join('\n'));
      }
      const file = resolve(root, options.baseline || await ask('Baseline JSON file path'));
      const baseline = loadBaseline(file);
      if (baseline.appId !== ctx.runtime.appId || baseline.baseVersion !== ctx.runtime.baseVersion || baseline.screenApi !== ctx.runtime.screenApi ||
          JSON.stringify(baseline.screenIds) !== JSON.stringify(ctx.runtime.screenIds)) throw new Error('Selected baseline belongs to a different app/base/screen contract.');
      const currentNative = nativeFiles(ctx);
      checkNative(baseline.nativeFiles, currentNative);
      const currentScreens = screenFiles(ctx);
      const changed = [...new Set([...Object.keys(currentScreens), ...Object.keys(baseline.screenFiles)])].filter(path => currentScreens[path] !== baseline.screenFiles[path]);
      if (!changed.length) throw new Error('No supported screen changes found.');
      console.log('Changed screen inputs:\n' + changed.map(path => `  ${path}`).join('\n'));
      const version = Number(options.version || await ask('Patch version (positive integer)'));
      if (!Number.isSafeInteger(version) || version <= 0) throw new Error('Patch version must be a positive safe integer.');
      const route = routeId(baseline);
      const tag = name(options.tag || `patch-${route.slice(0, 24)}-${version}`);
      const url = options.url || (ctx.settings.repository ? `https://github.com/${ctx.settings.repository}/releases/download/${tag}/screens.js` : await ask('Final HTTPS URL for screens.js'));
      const out = options.out ? resolve(root, options.out) : inside(root, `${ctx.settings.outputDir}/patches/${route}/${version}`);
      emptyOutput(out);
      gradle(root, ctx.settings.compileTasks);
      checkNative(currentNative, nativeFiles(ctx));
      checkNative(currentScreens, screenFiles(ctx));
      const bytes = readFileSync(inside(root, ctx.settings.programFile));
      if (sha256(bytes) === baseline.programSha256) throw new Error('Compiled program is unchanged.');
      if (!ctx.settings.publicKeyFile) throw new Error('Configure publicKeyFile to match the key pinned in your app.');
      const envelope = signedManifest(baseline, version, url, bytes, readFileSync(resolve(root, ctx.settings.signingKey)),
        readFileSync(inside(root, ctx.settings.publicKeyFile)));
      reserveVersion(root, baseline, version, () => {
        writeFileSync(join(out, 'screens.js'), bytes, { flag: 'wx' });
        saveJson(join(out, 'patch.json'), envelope);
      });
      console.log(`Signed patch saved: ${out}\nNothing uploaded. Target release tag: ${tag}`);
    }
  } finally { terminal?.close(); }
}
