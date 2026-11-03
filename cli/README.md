# PatchRelay CLI

An installable `patchrelay` command. No Python installation or Python commands.
Uses Node 22+ and the project's Gradle wrapper with JDK 17 and Android SDK 35.

Experimental alpha package: `@orbital_mcp/patchrelay`. The Android SDK is a
separate Maven dependency; installing it does not install this CLI.

## Run current source

```powershell
node .\cli\bin\patchrelay.mjs --help
```

Use this checkout's CLI for generated function contracts. Existing alpha installations predate these changes. A local package can be built with `npm pack ./cli`; no publication is needed. Signing keys are not package inputs. See [generated Kotlin integration](../docs/kotlin-workflow.md).

## Use

Run inside your prepared Android project:

```sh
patchrelay init
patchrelay baseline
# Edit patchable screen Kotlin.
patchrelay compare --baseline PATH_TO_BASELINE_JSON
patchrelay compare --baseline PATH_TO_BASELINE_JSON --diff
patchrelay patch
patchrelay catalog
```

`init` asks for module names, GitHub repository or HTTPS patch-host base URL,
signing-key location, stable signed-catalog HTTPS URL, channel and an existing
local catalog (or `new` for the first release). It records generated JS/APK/
BuildConfig/key paths, the release APK baseline pointer, and a patch output folder
under `<screens-module>/build/patchrelay`. Kotlin sources are discovered recursively,
not entered one file at a time. It writes
`patchrelay.config.json` without key contents and appends catalog URL/channel
settings to `local.properties`, preserving existing SDK path settings.
It also copies the Gradle build plugin and prints the one-time settings/app plugin
hooks. Also apply `dev.patchrelay.functions` to the JVM/JS shared module and add that module as an Android dependency. Apply those hooks so normal `assembleRelease` and `bundleRelease` builds
automatically preserve matching baseline records; no extra baseline command is
then required. This repository's demo already has the hooks. The plugin remains
limited to prepared screen modules, not arbitrary Kotlin screen rewriting.
It adds `.patchrelay/` and `.keys/` to Git ignore rules without removing existing
rules. Custom signing-key locations still need their own ignore protection.
It does not generate/rotate keys, install SDK hooks, migrate ordinary native
screens or configure private-host authentication. Review generated JS/APK/
BuildConfig paths before building. A custom Kotlin/JS export name or Gradle
variant needs corresponding paths at setup (or edits to `patchrelay.config.json`).

### Which files go where?

| File | Purpose | Upload to patch hosting? |
| --- | --- | --- |
| Kotlin source files | Developer writes screens, functions and navigation | No |
| `patchrelay.config.json`, `.patchrelay-tools/` | Setup creates project settings/build tools; commit with app source | No |
| Generated hooks, function contract, compiled build output | Build creates integration and program | No direct upload |
| Baseline directory (APK, JSON, bundled program, source ZIP) | Immutable private release backup | No |
| Private signing key | Developer-owned signing credential | Never |
| `screens.js` in the patch output directory | Complete compiled patch program | Yes, first |
| `patch.json` beside that program | Signed compatibility manifest | Yes, second |
| `catalog.json` from the catalog command | Signed discovery catalog | Yes, last, at stable catalog URL |

Upload the **patch output** `screens.js`, not a similarly named build file.
Output names are fixed; choose project input paths during setup and output folders
with `--out`. `patchrelay catalog` prints the exact three local paths and hosting
destinations. `patch` does not upload automatically; `patch --github` is the
GitHub Releases option. Retain older hosted patches while catalog routes
reference them. Public keys belong in the native app; private
keys and baseline archives stay in private storage.

`baseline` asks for a unique record name, builds/tests the configured screen
module, bundles its program, builds the APK, checks the native fingerprint and
saves APK, program, compatibility JSON and private source ZIP under
`.patchrelay/releases/baselines/NAME`. It changes the configured bundled JS asset
as part of preparing that native build. Back up the entire baseline directory
privately. Local output is not cloud backup. Do not publish the source ZIP.

`compare --baseline PATH` compares **all files recursively** under configured
native/screen inputs against that explicitly selected release. It lists every
`ADDED`, `MODIFIED` and `REMOVED` file, grouped by native/build inputs versus
supported screen inputs, with counts. There is no one-file or 100-file limit;
the test suite checks a 150-file change set. Missing declared inputs are reported.
Comparison follows baseline content hashes, including configured text line-ending
normalization, rather than the Git working tree or the last patch.

Add `--diff` to see actual added/deleted source lines for every changed file,
using the baseline's verified private source ZIP. It requires Git plus the JDK
`jar` tool, extracts sources into an isolated temporary directory and cleans it
afterward. Binary files get Git's binary-change notice. A normal comparison
performs no build, signing, version reservation or project writes. No application
source is changed by either mode. Files outside declared inputs are not included;
this is a released-app comparison, not a whole-repository or semantic-code audit.
Native changes require a new APK. Matching native hashes do not prove correctness:
function/signature compatibility and configured tests run during `patch`.

After setup and the first release APK build, the Gradle hook records an
`apk-record.json` pointer to the current baseline. `patch` uses that pointer,
reserves the next version, and writes `screens.js`, `patch.json` and an updated
`catalog.json` directly under `<screens-module>/build/patchrelay/patches`.
On a successful patch build, it replaces the previous generated patch folder;
the stable working catalog remains at its separate configured path.
For a non-GitHub host, setup stores the HTTPS base URL; patch files are
versioned under the generated release tag. The current signed catalog is kept at
the configured local catalog path so later patches preserve earlier base routes.
The command prints the three exact files and their hosting destinations. For a
personal server, upload `screens.js` and `patch.json` into the tag-named folder
under `patchBaseUrl`, then replace `catalog.json` at `catalogUrl` last. The APK
must be configured with the stable `catalogUrl`, never `patchBaseUrl`. See
[`docs/update-routing.md`](../docs/update-routing.md) for a concrete server layout
with multiple patch folders and the first-install checklist. Each run uses a new
tag folder; keep prior folders unchanged while the catalog references them. The
catalog URL stays the same and its signed contents are replaced after each upload.
Explicit `--baseline`, `--version`, `--url` and
`--out` still override the defaults. Native/contract changes require a new APK
baseline. Output directories are exclusive; prior releases are never overwritten.

GitHub artifact URLs use the configured repository and the convention
`patch-ROUTE_HASH-VERSION`; the route hash binds app ID, base version and exact
native fingerprint, avoiding collisions between apps/builds. `--tag` overrides
that release tag. Run `patch --github` to create a GitHub Release containing
`screens.js`, `patch.json` and the updated `catalog.json`. It uses the `origin`
GitHub repository and current branch, and publishes the catalog at the stable
`releases/latest/download/catalog.json` URL. GitHub credentials must be available
through Git Credential Manager or `GITHUB_TOKEN`. The command saves this catalog
URL in ignored `local.properties` for the next APK build. Install an APK built
with this URL before expecting it to discover release patches. For another HTTPS
host, the command prints exact upload paths and destinations. See
[`docs/update-routing.md`](../docs/update-routing.md) for how the app resolves
catalog → manifest → program and how to configure personal hosting.

Patch outputs from the configured one-command flow are under
`<screens-module>/build/patchrelay/patches`. `screens.js` is the complete
compiled program for the current supported screens, so it contains all current
screen changes together. Version numbers still increase for each exact APK base;
the local output folder is cleaned and reused, while the signed catalog records
the active manifest route.
A locked, local version record under `.patchrelay/versions` prevents reusing or
lowering a version for the same exact base even with a different output folder.
A failed write after reservation consumes that version; choose a higher one.
Back up these records. They cannot detect versions published from another
machine unless you retain the latest published records/catalog. All channels
for an exact base share the version sequence.

## Stable update discovery

`patchrelay catalog` remains available for manual workflows. It asks for a signed
patch manifest, the existing signed catalog (or explicit `new` for the first
one), channel and final manifest HTTPS URL.
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
