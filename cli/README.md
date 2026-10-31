# PatchRelay CLI

An installable `patchrelay` command. No Python installation or Python commands.
Uses Node 22+ and the project's Gradle wrapper with JDK 17 and Android SDK 35.

Experimental alpha package: `@orbital_mcp/patchrelay`. The Android SDK is a
separate Maven dependency; installing it does not install this CLI.

## Install a built package

```sh
npm install --global /path/to/orbital_mcp-patchrelay-0.1.0-alpha04.tgz
patchrelay --help
```

Public alpha installation:

```sh
npm install --global @orbital_mcp/patchrelay@alpha
```

Package page: https://www.npmjs.com/package/@orbital_mcp/patchrelay.
The `alpha` tag identifies an experimental release rather than a stable one.

Publisher setup requires `npm login`, verification that the account controls the
`@orbital_mcp` scope, and `npm publish --access public --tag alpha` from the CLI
package. Never paste npm credentials into this repository or an app. Ownership
and authentication must be established before advertising a working public link.

## Use

Run inside your prepared Android project:

```sh
patchrelay init
patchrelay baseline
# Edit patchable screen Kotlin.
patchrelay patch
patchrelay catalog
```

`init` asks for module names, GitHub hosting repository (or HTTPS hosting) and
signing-key location, stable signed-catalog HTTPS URL and channel. It writes
`patchrelay.config.json` without key contents and appends catalog URL/channel
settings to `local.properties`, preserving existing SDK path settings.
It also copies the Gradle build plugin and prints the one-time settings/app plugin
hooks. Apply those hooks so normal `assembleRelease` and `bundleRelease` builds
automatically preserve matching baseline records; no extra baseline command is
then required. This repository's demo already has the hooks. The plugin remains
limited to prepared screen modules, not arbitrary Kotlin screen rewriting.
It adds `.patchrelay/` and `.keys/` to Git ignore rules without removing existing
rules. Custom signing-key locations still need their own ignore protection.
It does not generate/rotate keys, install SDK hooks, migrate ordinary native
screens or configure private-host authentication. Review generated JS/APK/
BuildConfig paths before building. A custom Kotlin/JS export name or Gradle
variant needs corresponding settings changes.

`baseline` asks for a unique record name, builds/tests the configured screen
module, bundles its program, builds the APK, checks the native fingerprint and
saves APK, program, compatibility JSON and private source ZIP under
`.patchrelay/releases/baselines/NAME`. It changes the configured bundled JS asset
as part of preparing that native build. Back up the entire baseline directory
privately. Local output is not cloud backup. Do not publish the source ZIP.

`patch` lists available baseline paths and asks you to choose one explicitly,
then asks for a positive patch version. It never selects a record automatically.
It rejects native/contract changes and altered source archives, compiles/tests
the full current supported screen program and signs `screens.js` plus `patch.json`.
Output directories are exclusive; prior releases are never overwritten.

GitHub artifact URLs use the configured repository and the convention
`patch-ROUTE_HASH-VERSION`; the route hash binds app ID, base version and exact
native fingerprint, avoiding collisions between apps/builds. `--tag` overrides
that release tag. For another HTTPS host,
the command asks for the actual final `screens.js` URL. Nothing is uploaded and
no hosted catalog is changed by these commands. The app's catalog endpoint is
configured separately in its native build; it is not the JS artifact URL.

Patch outputs are under `.patchrelay/releases/patches/ROUTE_HASH/VERSION`.
A locked, local version record under `.patchrelay/versions` prevents reusing or
lowering a version for the same exact base even with a different output folder.
A failed write after reservation consumes that version; choose a higher one.
Back up these records. They cannot detect versions published from another
machine unless you retain the latest published records/catalog. All channels
for an exact base share the version sequence.

## Stable update discovery

`patchrelay catalog` asks for a signed patch manifest, the existing signed catalog
(or explicit `new` for the first one), channel and final manifest HTTPS URL.
It verifies signatures/artifact hashes, preserves routes for older bases and
updates only that patch's exact app/base route. It rejects a route downgrade,
wrong app/channel, ambiguous routes and more than 64 supported base builds.
It writes a new signed `catalog.json` locally, never overwriting hosted data.

Use one stable catalog URL per app/channel. Different apps can share one GitHub
repository while using separate catalogs, signing keys and immutable patch
releases. Upload/verify `screens.js` and its `patch.json` first, then replace
the hosted catalog. Keep every artifact still referenced by an older base.
GitHub asset replacement is not atomic; a brief failed check must leave the
current app working. Private-source hosting still needs accessible HTTPS patch
delivery or a separate authenticated gateway; GitHub credentials aren't embedded.

The prepared host calls `updates.stageCatalog(catalogUrl, channel)` off the UI
thread. The SDK verifies the catalog, selects the exact installed fingerprint,
then verifies the selected manifest and downloads only the latest complete
program. No match means no update. Existing version/signature checks still apply.
This supports skipping intermediate patches, not arbitrary data migrations.
The new demo checks once per process after healthy first rendering, silently;
the downloaded patch activates on a later process start, not mid-session.

Automation can supply explicit values:

```sh
patchrelay baseline --name android-v1
patchrelay patch --baseline .patchrelay/releases/baselines/android-v1/baseline.json --version 1
```

There is no default signing key material and no default publisher repository.
The configured `publicKeyFile` must contain the Base64 DER public key pinned in
the app; signing refuses a mismatched private key. The key file format is the existing PatchRelay Base64 DER PKCS#8 RSA private key
(minimum 3072 bits). Keep it out of Git and releases. The CLI signs locally;
private keys are never sent to Maven, npm or the patch host.

Normal Gradle baselines are stored at
`.patchrelay/releases/baselines/APP_ID/VARIANT/apk-ARTIFACT_HASH/` or
`aab-ARTIFACT_HASH/`. Select the desired `baseline.json` explicitly in `patch`.
Both formats work for patch compilation; AAB files themselves are not installed
by this tool. `patchrelay baseline` remains useful for a developer-named APK record.

## Recover a lost record

```sh
patchrelay baseline --from YOUR-COMMIT
```

You supply the record name and original APK when asked. Recovery checks out only
that explicitly supplied revision into a detached worktree, using its project
settings when present or the current module-layout settings otherwise. It uses
the selected revision's runtime contract and sources. Current branch/edits remain
untouched. The rebuilt APK must match the original APK hash exactly. Different
signing/toolchain output is rejected; without a matching release, ship a new base.
Recovery worktrees and build diagnostics stay under `.patchrelay/recovery/`.

## Project contract

This CLI is independent of this repository's Python tools, but it is **not an
automatic integration plugin for arbitrary Android apps**. Projects still need:

- Prepared Kotlin/JS screens and explicit native hooks.
- `runtime.json` format 1 / screen API 2 with app/base/screen identity, declared
  native inputs and text-normalization extensions.
- Gradle generation of `HOST_SHA256`, `BASE_VERSION` and `APPLICATION_ID` in the
  configured BuildConfig, following the existing sorted SHA-256 contract.
- An Android host using the SDK and configured trust keys/update endpoint.

The CLI does not scan ordinary Kotlin to discover patchable methods. Multiple
app/variant projects need separately reviewed configuration and release records.
Baseline selection remains manual. Signed catalog authoring/routing is implemented;
automatic remote uploads and arbitrary app integration are still pending.

## Build this CLI from source

```sh
cd cli
npm test
npm pack
```

The npm tarball contains CLI runtime files, the Gradle build-plugin source,
documentation and license: no Android SDK/app implementation, signing keys,
baseline archives or private R8 mappings.
The CLI is JavaScript distributed to developers, so its runtime is inspectable.
Android SDK AAR obfuscation is a separate build step, not protection for CLI code.
