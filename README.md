# PatchRelay Screens

Experimental replacement screens for native Android apps, authored in Kotlin and delivered from GitHub as signed interpreted programs. React Native, Flutter, downloaded DEX and native-library patching are not implemented targets.

The current proof replaces a checkout screen with two bugs: a missing payment button and an incorrect total. The developer changes only `screens/src/commonMain/kotlin/Checkout.kt`. Kotlin/JS compiles the supported screen module; the installed interpreter returns a validated widget tree and the Android renderer creates native TextViews and Buttons. Events travel back to the Kotlin-authored program. This is a small supported screen API, not an automatic converter for existing Activity or Android Compose code.

## Structure

- `screens/`: Kotlin screen logic and the small text/button/column API; compiled to one standalone JS program.
- `sdk/`: signed staging, compatibility checks, startup fallback, local interpreter and native widget renderer.
- `demo/`: native screen host. Its checked-in `baseline-screen.js` contains the original buggy screen; current Kotlin source contains the fix.
- `tools/screens.py`: base snapshot and patch builder. It detects changed screen source files, rejects native/asset/compiler changes, then compiles the supported module.
- `tools/Publisher.java`: RSA signing; schema 3 explicitly targets `kotlin-screen-js` and registered screen IDs.

[Architecture and limitations](docs/architecture.md). [Phone verification](docs/screen-engine-verification.md).

## Build and check

Requires JDK 17, Android SDK 35, Python 3 and Node 20+. Kotlin 1.9.23, AGP 8.6 and Gradle 8.9 are pinned. The app needs an installed Android WebView provider. No React Native runtime, npm packages or downloaded native engine are used.

```powershell
.\gradlew.bat :screens:jvmTest :screens:compileProductionExecutableKotlinJs :sdk:testDebugUnitTest :sdk:assembleRelease :demo:assembleRelease
node tools/test-screen-program.mjs demo/src/main/assets/baseline-screen.js baseline
node tools/test-screen-program.mjs screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js fixed
python tools/test_screen_build.py
```

The demo release is non-debuggable and signed with a development key. The native base must be installed once before screen updates work. Its APK still contains normal Android native code; downloaded updates contain interpreted screen code, not DEX.

## Create a screen patch

The repository contains the fixed checkout source and the original base's compatibility snapshot at `demo/release-baseline.json`. With the matching private signing key available locally:

```powershell
python tools/screens.py patch --baseline demo/release-baseline.json --out dist/native-screens-patch-1
```

Use a fresh output directory, increasing version and unique release tag for each later patch, for example `--version 2 --tag native-screens-patch-2`. Existing signed outputs are not overwritten. The example's functional checker currently expects the demonstrated checkout fix; adapt its assertions for intentional future behavior changes.

The builder checks source content, not filenames alone. It rejects changes to SDK/native host code, manifests, native assets and compiler settings relative to the baseline. Eligible common Kotlin screen code is recompiled as a complete module; this is not a binary delta or arbitrary method extraction.

A new base can be captured with `python tools/screens.py base --out dist/NEW_BASE` while the screen source is in its baseline state. The example checks expect the deliberately buggy baseline. Allocate a new base compatibility ID in the host and publisher workflow for each incompatible native release; this prototype is pinned to `native-screens-v1`.

## Hosting

Upload `screens.js` and `patch.json` as immutable assets under the signed release tag. Publish the matching base APK and its snapshot for testing. Only after assets are available, publish the signed manifest to `channels/native-screens-v1.json`. The demo polls that exact raw GitHub channel, separate from historical prototype release channels. Future patches use new artifact tags and a newer signed channel manifest. The SDK can use any HTTPS manifest/artifact host.

The private key stays in ignored `.keys/`; the app pins `demo/patch-public-key.txt`. New users must generate their own key pair with `java tools/Publisher.java keygen PRIVATE PUBLIC` and build their base with that public key. Do not replace this repository's demo key without rebuilding the base.

## Current boundaries

One screen (`checkout`), one complete program, column/text/button widgets and local click/state behavior. No images, arbitrary native APIs, external navigation, new permissions, dependency updates or general Compose/Activity translation. Demo payment confirmation changes local display state only; it performs no transaction.

WebView is used solely as the local interpreter. It has file/content/network loads disabled, no JavaScript-to-Java interface and no external navigation. Native widgets come from validated program output. Programs are signed trusted code, not arbitrary third-party scripts; this is not a proof of complete sandbox isolation.

Health is confirmed after a valid native tree renders. Failed or unconfirmed patched startup falls back, preserving the accepted-version high-water mark. Recovery does not undo persistent app data or external side effects. An interpreter timeout prepares fallback and requires a process restart. State is currently in-memory and resets on restart. One designated process only; key rotation, broad compatibility, resource-exhaustion recovery and production operations need further work.

Google Play has an interpreter/VM exception conditioned on indirect Android API access and compliance with other policies. This experiment may fit that model; it has not received Play approval. [Google policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).

Earlier updater experiments remain in Git/release history; they are not part of the active SDK.
