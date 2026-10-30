# PatchRelay Screens

Signed replacement screens for native Android apps, authored through a small Kotlin screen API. Kotlin/JS produces an interpreted program; installed Android widgets render its validated output. No downloaded DEX, React Native or Flutter runtime.

**Experimental.** V1 passed a real-phone download, activation and restart test. API 2 adds richer UI and stronger compatibility checks; its device verification is tracked separately. Existing Activity/Android Compose screens must be integrated with this API; arbitrary Kotlin methods are not patchable.

## Components

- `screens/`: screen logic, JSON screen API and common Kotlin tests.
- `sdk/`: downloads, signatures, staging/fallback, interpreter, validation and native rendering.
- `demo/`: native host and bundled baseline program.
- `runtime.json`: app/base identity, channel, screen IDs and native compatibility inputs.
- `tools/screens.py`: release snapshots and compatible patch builds.
- `tools/Publisher.java`: dependency-free RSA signer.

API 2 supports nested columns, rows and cards; styled text; text inputs; checkboxes; enabled/disabled buttons. Stable widget IDs preserve native views, focus and cursor during ordinary updates. Input events queue in order; adjacent changes to the same field are coalesced. Only the latest queued result is rendered.

[Architecture](docs/architecture.md) · [V1 phone proof](docs/screen-engine-verification.md) · [Production readiness](docs/production-readiness.md)

## Build and check

Requires JDK 17, Android SDK 35, Python 3, Node 20+ and an Android WebView provider. Gradle, AGP and Kotlin versions are pinned. No npm runtime dependency.

```powershell
.\gradlew.bat :screens:jvmTest :screens:compileProductionExecutableKotlinJs :sdk:testDebugUnitTest :sdk:assembleRelease :demo:assembleRelease
node tools/test-screen-program.mjs demo/src/main/assets/baseline-screen.js baseline
node tools/test-screen-program.mjs screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js fixed
python tools/test_screen_build.py
```

The demonstration APK uses a development signing key. A new base APK is required when installed renderer capabilities change. API 2 is incompatible with the published v1 base; v1 release assets remain unchanged.

## Release workflow

1. Set the new native release identity and channel in `runtime.json`.
2. With baseline screen source in place, build a base:
   `python tools/screens.py base --out dist/native-screens-v2-base`.
3. Preserve its APK and `baseline.json`. Install that base once.
4. Change only supported screen Kotlin. Build:
   `python tools/screens.py patch --baseline demo/release-baseline.json --out dist/native-screens-v2-patch-1 --version 1 --tag native-screens-v2-patch-1`.
5. Upload `screens.js` and `patch.json` to that immutable artifact release.
6. After artifacts are available, publish the signed `patch.json` at the channel URL. Stage in the app; activate on next process start.

Current source contains the rich checkout patch. `demo/fixtures/Checkout.v2.baseline.kt` preserves the simpler API-2 base source; `Checkout.baseline.kt` is historical v1. The example's compiled-program checker asserts checkout behavior; change its assertions when intentionally changing that behavior.

Native source, packaged assets, compiler settings and runtime configuration are hashed. Patches must match the base snapshot. The same host fingerprint is embedded in the APK and signed into each patch, preventing a patch built against another host from activating. This fingerprints the declared source/build contract, not the complete APK binary or a reproducible toolchain. APK hashes are recorded separately.

Signing key stays outside Git in ignored `.keys/`; the public key is pinned in the base. Generate your own pair with `java tools/Publisher.java keygen PRIVATE PUBLIC`. Changing the pinned key requires a new base.

## Boundaries

One checkout demonstration, complete-program replacement, in-memory screen state. No automatic Compose conversion, images, arbitrary native API bridge, dependency updates or new permissions. Payment confirmation is a local demonstration; no real transaction occurs.

Signed programs are trusted code. WebView network/file/content access and external navigation are disabled; no JavaScript-to-Java interface is exposed. These restrictions are not proof of complete isolation.

Health is confirmed after the initial validated tree is attached. Startup fallback does not undo persistent data or external side effects, and late failures can still require a corrective release. Interpreter timeouts require process restart.

Store eligibility remains unverified. See the production-readiness checklist before depending on this system for users.
