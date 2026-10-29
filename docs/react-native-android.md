# React Native on Android

First integration target: React Native **0.82.1**, React 19.1.1, Hermes, new architecture, Android API 26+. This is an Android prototype, not an iOS adapter or a published npm package.

## Responsibilities

- `core/`: bounded HTTPS download, signature verification, signed compatibility metadata. Also used by the existing Android DEX adapter.
- `react-native/android/`: persistent JS bundle staging, selected file path, health confirmation and conservative startup rollback. No dependency on React Native classes.
- `examples/react-native/`: pinned React Native host, a small native bridge and before/after JS screens. Its standalone Android build uses the React Native template's toolchain rather than upgrading the existing DEX demo's toolchain.

Schema 1 remains the DEX manifest. React Native uses schema 2 with signed `runtime: "react-native-android"`, `artifactType: "js-bundle"`, `appId`, exact `baseVersion`, increasing `version`, `artifactUrl`, `sha256`, and `apiVersion: 1`. Both use the existing Base64 payload/signature envelope and SHA256withRSA. Each adapter rejects the other's manifest schema.

The example compatibility ID is `rn-0.82.1-hermes-base1`. Change this identifier whenever the React Native/Hermes version, native modules, native dependencies, resources, architecture or application contract changes. A JS-only npm dependency can ship in a bundle; a dependency requiring new native code cannot.

## Build

Requires JDK 17+, Node >=20.19.4, Android platform 36, NDK 27.0.12077973, and SDK/build tooling requested by the pinned template. Existing root Android modules still target platform 35. Configure `JAVA_HOME`, `ANDROID_HOME` or ignored `local.properties` in each Android build.

```powershell
cd examples/react-native
npm ci
npm test -- --runInBand
cd android
.\gradlew.bat :app:assembleRelease -PreactNativeArchitectures=arm64-v8a,x86_64 --max-workers=2
```

The example release uses a development signing key. Its native base includes the orange **Original React Native bundle** screen. It does not use Metro at runtime. Do not distribute this example signing configuration as a production app.

From the repository root, build the blue replacement JS bundle:

```powershell
.\tools\build-react-native-patch.ps1
```

This bundles `index.patch.js`, checks that no new asset pack was emitted, then signs the exact JS bytes using the existing private demo key. Output: `dist/react-native-android-v1/index.android.bundle` and `patch.json`. Existing signed manifests are not overwritten. `Publisher.java sign-js` is distinct from the DEX-only `sign` command.

Publish both files together under the dedicated **prerelease** tag `react-native-android-v1`. The example fetches that exact manifest URL, not the DEX demo's latest release. Keep the DEX channel unchanged.

## Host integration

Create one `ReactNativeUpdates(context, baseCompatibilityId, pinnedPublicKey)` per app process. Before constructing `ReactHost`, call `start()` once on a worker thread; pass its result into `getDefaultReactHost(jsBundleFilePath = selectedPath, ...)`. Null selects the APK's normal bundled JavaScript. Keep the selected path and host stable for the process lifetime.

Expose `stage(manifestUrl)`, `pendingVersion()`, `activeVersion()` and `markHealthy()` through the app's native-module integration. The example's `UpdatePackage` shows the exact bridge for this pinned version. Invoke `markHealthy()` only after the root component mounts successfully. If startup is not confirmed, the next process start rolls back while preserving the version high-water mark. After staging, fully restart the app process; no live reload is implemented.

Verification flow: record the release APK hash and install timestamp, open the orange screen with active bundle 0, check for update, force-stop/reopen, then observe blue **Updated from GitHub** with active bundle 1. The native APK hash and install timestamp must stay unchanged during the JS update.

## Boundaries

This first adapter supports one self-contained UTF-8 Metro JavaScript bundle, up to 16 MiB, running on the already-installed Hermes engine. It does not yet publish downloaded asset packs, Hermes bytecode patches, new native modules, permissions, Android resources or native-library changes. New assets require an explicit asset-pack resolver; do not assume Metro's Android resources become downloadable automatically.

Health confirmation covers startup, not every later exception. OS termination before confirmation can trigger conservative rollback. Files, databases, side effects and destructive migrations are not rolled back. One app process only; key rotation, bundle cleanup and crash telemetry remain pending.

## Verification records

Root JVM tests cover manifest schema/runtime/app/base rejection and tampering. Android instrumentation fixtures cover confirmed-start persistence, unconfirmed startup rollback, tampered-bundle recovery and wrong-runtime fallback. Runtime rendering and public GitHub download results are recorded separately after execution; a built APK alone is not evidence of an applied update.
