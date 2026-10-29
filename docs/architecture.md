# Architecture

PatchRelay has one release protocol and separate runtime adapters. Language is not the adapter boundary: Java and Kotlin share Android's DEX runtime. React Native and Flutter also contain native platform code; updating their managed code does not update that native code.

## Current ownership

| Location | Owns | Status |
| --- | --- | --- |
| `core/` | Shared HTTPS and signed-manifest verification | Implemented; used by Android DEX and React Native |
| `react-native/android/` | Android JS bundle staging and startup rollback | Initial adapter; pinned release example |
| `sdk/` | Android DEX verification, staging, activation, rollback | Implemented; Java and Kotlin callers |
| `tools/Publisher.java` | Signed Android manifest production | Implemented |
| `tools/PatchBuild.psm1` | Shared Android DEX packaging and signing commands | Implemented |
| `tools/build-patch.ps1` | Normal Android patch build entry point | Implemented |
| `tools/publish-github.ps1` | GitHub release upload | Implemented; hosting separate from loading |
| `demo/` | Example Android integration and visible image test | Implemented; not SDK code |
| `patch/` | Example patch plus isolated stress fixtures | Test/example code; not SDK code |
| `visual/` | Historical separate-app comparison fixtures | Test code; not SDK code |
| Other `tools/build-*.ps1` | Reproduce specific experiments and their checks | Test tooling; not extra SDKs |

Existing Gradle module names, Kotlin/Java package names, manifest formats and published URLs remain stable. No empty platform modules or generic loader framework are needed before another adapter works.

Packaging verification on Windows, after building `:patch:jar`:

```powershell
.\tools\test-patch-build.ps1 -AndroidSdk $env:ANDROID_HOME
```

This creates disposable local signing keys and verifies DEX generation, signed metadata, signature verification and rejection of attempts to overwrite signed outputs. It does not upload releases or install apps.

## Runtime adapters

| Adapter | Artifact | Compatibility boundary | Current status |
| --- | --- | --- | --- |
| Android (Java/Kotlin) | DEX implementation of `Patch` | Exact native base/operation contract and parent runtime | Implemented prototype |
| React Native | JavaScript bundle and associated assets | Compatible installed React Native runtime and native modules | Initial Android implementation; see [setup](react-native-android.md) |
| Flutter | Dart patch supported by a specific engine/toolchain | Installed Flutter engine, Dart execution mode and native plugins | Research required; ordinary release AOT code is not a loadable DEX patch |
| iOS native | Platform-specific update mechanism | Installed signed executable and platform restrictions | Not implemented |

React Native native-module changes and Flutter native-plugin changes belong to the native platform path. New permissions, components and incompatible native ABI changes require a new base app with the current implementation. A downloaded asset can be shared conceptually across adapters, but each host must explicitly resolve the downloaded override before its bundled asset.

## Shared release contract

The reusable boundary is data and behavior, not a Kotlin class that every runtime must inherit:

1. Host supplies application identity, installed base compatibility identifier, trusted signing key and HTTPS manifest URL.
2. Verify the envelope signature before trusting metadata; reject incompatible targets and non-increasing versions.
3. Download bounded artifacts, check their hashes, stage durably, then activate through the runtime adapter.
4. Confirm health only after the host exercises patched startup. Preserve a recoverable previous implementation.
5. Report staged/active versions and actionable errors. Code rollback does not imply data rollback.

Current schema **1** is specifically the Android DEX contract: `schema`, `apiVersion`, `appId`, `baseVersion`, `version`, `entryPoint`, `artifactUrl`, `sha256`. The envelope contains Base64 `payload` and `signature`; sign exact UTF-8 payload bytes using SHA256withRSA. Existing clients and published patches retain this format.

The React Native Android adapter introduces schema 2 with signed `runtime: react-native-android` and `artifactType: js-bundle`; schema 1 stays DEX-only. Clients must reject unknown targets; never infer runtime compatibility from language or filename. Specify fixtures that each implementation must accept/reject before sharing a release channel. Do not send future React Native/Flutter manifests to existing schema-1 Android clients.

## Development sequence

1. Keep Android SDK behavior stable while consolidating build and publication code.
2. Finish Android failure/recovery and compatibility tests before calling it production ready.
3. Implement React Native bundle loading against a fixed native runtime; validate the shared contract with cross-runtime fixtures.
4. Investigate Flutter's actual engine/toolchain patching mechanism before promising Dart or mixed native updates.

This structure supports adding adapters without moving test scenarios into the SDK or promising the same code-loading mechanism on every runtime.
