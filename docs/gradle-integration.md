# Automatic baseline records from Android release builds

The `dev.patchrelay.baseline` Gradle plugin integrates the existing prepared
Kotlin/JS screen module with normal APK and Play Store AAB builds. It records a
base automatically; developers still explicitly choose which record to patch.
It does not rewrite arbitrary Android screens or discover native method hooks.

## One-time setup

`patchrelay init` copies the included build-plugin source into
`patchrelay-gradle-plugin/` (or reuses this repository's existing `gradle-plugin/`),
adds it to the declared native inputs and prints the two required Gradle hooks.
It does not rewrite arbitrary settings/app build scripts automatically. Add:

```kotlin
// settings.gradle.kts, inside pluginManagement
includeBuild("patchrelay-gradle-plugin")
```

```kotlin
// app/build.gradle.kts, inside plugins
id("dev.patchrelay.baseline")
```

This repository already applies those hooks to `demo` with `gradle-plugin/`.
Commit the copied plugin, runtime contract and module configuration with the
native release. Keep credentials and `.patchrelay/` out of Git. The plugin is
bundled with the local CLI package; no Gradle Plugin Portal publication exists.

The prepared app still needs `buildFeatures.buildConfig = true`, its base identity,
screen IDs, public keys and SDK lifecycle integration. The plugin supplies the
release `HOST_SHA256` through generated `PatchRelayBuild.java`, so it is calculated
after the current program is bundled rather than too early during configuration.
Defaults target module `screens` and `PatchRelayScreens.js`; projects with other
layouts must configure the `patchrelayBaseline` extension's `programFile`,
`baselineAsset`, `screenInputs`, `compileTasks` and `runtimeFile` consistently with
their CLI settings. It validates the variant application ID against that runtime.

## Normal builds

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

Use `demo` instead of `app` in this repository. Both commands now:

1. Compile/test the prepared screen module.
2. Copy the current JS program into the configured bundled asset.
3. Calculate the exact native/screen contract and generate the host constant.
4. Build the APK/AAB, then verify that its actual ZIP asset matches the program.
5. Save an immutable baseline record tied to the exact output artifact hash.

Thus a native release includes the latest supported screen fixes in its bundled
program. It intentionally updates the configured source asset during building.
Patches do not update that asset: they compile the changed screen module only.

Records are saved outside `build/`:

```text
.patchrelay/releases/baselines/<appId>/<variant>/apk-<artifactHash>/
.patchrelay/releases/baselines/<appId>/<variant>/aab-<artifactHash>/
```

Each contains `baseline.json`, `baseline-screen.js`, `baseline-sources.zip` and
`screen-base.apk` or `screen-base.aab`. The JSON stores native/screen file hashes,
full host fingerprint, program/source-archive hashes, variant and artifact hash.
The normal `clean` task does not erase these private records. Back them up outside
the workstation; local storage is not automatic remote backup. Never publish the
source archive with SDK binaries or patches.

An identical rebuild verifies/reuses its existing record; it does not overwrite
it. Source changes during a build, a mismatched embedded program, split APK output,
credential-file inputs or damaged existing records cause a build error rather
than a silently trusted baseline. Diagnostics/partial failed output are retained.
Large artifact hashes are streamed rather than loading the whole APK/AAB in memory.

The build also writes a convenience result file under
`<module>/build/outputs/patchrelay/<variant>/apk-record.json` or `aab-record.json`.
This reports what that build produced; it is not automatic patch-baseline selection
and is not the durable record itself.

## Patches and Play Store updates

After editing supported Kotlin, run `patchrelay patch` and explicitly supply or
select the saved `baseline.json`. Both APK and AAB format-3 records are accepted.
The patch compiler checks the recorded native contract and builds the complete
current screen snapshot. Native changes require another APK/AAB release instead.

When Play distributes an AAB, the installed APKs differ from the uploaded bundle's
file hash. Patch compatibility uses the embedded host fingerprint and registered
contract, not the device APK's hash. APK/AAB hashes identify release artifacts for
recordkeeping. Native identity changes reset old patch selection as documented in
[update routing](update-routing.md).

## Verification and limits

Tested locally with Gradle 8.9, AGP 8.6, Kotlin 1.9.23 and JDK 17: actual demo APK
and AAB builds, matching generated/program/artifact fingerprints and repeated-build
record reuse. A deliberately wrong embedded program was rejected without replacing
the valid record. CLI tests cover generated host-constant resolution and copying
the plugin during init. Android SDK and CLI regression suites also pass.

Release variants only; one APK output per variant. Product flavors need a matching
runtime contract; the plugin does not invent identities for them. Configuration
cache support, split-APK record sets, library-host integrations, production signing
setup and automatic arbitrary-screen conversion are not implemented. AAB recording
does not prove a real Google Play upgrade: no new APK has been installed on the
phone and nothing has been uploaded or published.
