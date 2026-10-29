# PatchRelay

Self-hosted app updates, delivered from GitHub Releases or your own HTTPS server.

**Experimental Android Java/Kotlin prototype.** Patches replace implementations behind an interface integrated into the original app. No APK installation is needed for these prepared code paths. This is not arbitrary Java/Kotlin method replacement, a compiler plugin, or a universal native patcher.

## Current scope

- Android 8+ Java SDK, callable from Kotlin.
- Separate Java patch compilation into DEX; Kotlin implementations must also supply their runtime dependencies in the original app or patch.
- RSA-3072 signed manifests; SHA-256 artifact verification; pinned public key in the installed app.
- App ID, exact base build, API version, increasing patch version checks.
- HTTPS downloads with size/time limits and HTTPS-only redirects.
- Staging now, activation on next process launch.
- Unconfirmed launch rolls back on the next launch; failed loading restores prior/built-in implementation.
- GitHub-hosted demo APK, SDK AAR, patch DEX, signed manifest.

This code-patching mode targets **directly distributed Android apps**. Google Play prohibits downloading executable DEX/JAR/native code outside its update mechanism: [policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en). No iOS, React Native, or Flutter SDK is implemented yet.

## Build

Requires JDK 17, Android SDK platform 35, build tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`; or set `sdk.dir` in ignored `local.properties`.

```powershell
.\gradlew.bat :sdk:testDebugUnitTest :sdk:assembleRelease :demo:assembleRelease :patch:jar
```

The demo release is **non-debuggable but signed with a development key**, for testing only. Production apps need their own release signing configuration.

## Integrate

Add the SDK AAR to your Android app. Define a stable operation contract shared by the built-in implementation and patch. Call from a worker thread, once per process:

```java
PatchRelay relay = new PatchRelay(context, "your-exact-build-id", publicKeyBase64);
Patch logic = relay.start((operation, input) -> originalImplementation(operation, input));
String result = logic.invoke("your-operation", input);
// After the patched startup path has succeeded:
relay.markHealthy();
// Stage an update for the next process launch:
boolean staged = relay.stage(manifestHttpsUrl);
```

Use one SDK instance in one designated app process. Persist and retain that instance/implementation across activity recreation. SDK disk/network operations belong on worker threads. `baseVersion` must change whenever native app code or the contract changes. Patch classes must have unique names not already packaged in the app, implement `dev.patchrelay.Patch`, and expose a public no-argument constructor. Do not package another copy of the interface in a patch.

Kotlin apps can implement/call the same Java interface. Kotlin patch compilation itself is not configured in this prototype.

## Publish a patch

Generate your own signing keys. The private key must stay outside version control; `.keys/` is ignored. The checked-in demo public key belongs to this repository's demo releases; it cannot sign your patches.

```powershell
java tools/Publisher.java keygen .keys/patch-signing.key my-public-key.txt
```

Embed your public key in the original app before distribution. To build this demo with a new key, use `demo/patch-public-key.txt` as the public-key output path (move the existing demo public key first; key generation refuses overwrite).

Change the implementation in `patch/`, then:

```powershell
.\tools\build-patch.ps1 `
  -ArtifactUrl https://github.com/OWNER/REPO/releases/download/patch-1/classes.dex
```

Upload `dist/classes.dex` and `dist/patch.json` to that GitHub release, using GitHub's website or `gh release create`. App fetches `https://github.com/OWNER/REPO/releases/latest/download/patch.json`. A regular HTTPS server can serve the same two files without GitHub. Publish artifacts before exposing the manifest. Keep releases immutable; increment patch versions for fixes and rollbacks.

`Publisher.java sign` accepts private key, DEX, app ID, exact base version, positive patch version, entry-point class, HTTPS artifact URL, output manifest. Signature covers the exact UTF-8 payload bytes encoded in the envelope; clients verify before interpreting metadata.

## Try the hosted demo

Install the APK from [Releases](https://github.com/jaypal1046/patchrelay/releases). Install once; the patch itself requires no installer.

```powershell
adb shell am start -n dev.patchrelay.demo/.MainActivity --es manifestUrl https://github.com/jaypal1046/patchrelay/releases/latest/download/patch.json
# Wait until the app says the update is verified, then restart its process:
adb shell am force-stop dev.patchrelay.demo
adb shell am start -n dev.patchrelay.demo/.MainActivity
```

Original message becomes `Patched Java logic: hello PatchRelay`. The manifest URL is supplied explicitly in this technical demo; there is no update-settings UI yet.

## Rollback and limitations

`markHealthy()` confirms startup only. If a process exits before confirmation, the next launch conservatively treats it as a failed boot, including OS kills. Errors occurring after confirmation require app-managed handling or a newer corrective patch. There is no runtime exception interception or guarantee against every crash. Rollback preserves the highest accepted version so an older downloaded manifest cannot replay a rejected patch.

Patches execute with the app's full privileges; signing keys are a critical trust boundary. Private signing keys are currently unencrypted local files: protect them with OS access controls and back them up securely. HTTPS servers are untrusted for code authenticity; possession of the signing key authorizes code execution. Key rotation/revocation is not implemented; change pinned keys through a full app release.

Single process only. No background scheduler, automatic method instrumentation, native library/permission updates, patch history cleanup, or app-data migration rollback. Patch files remain in internal storage; remove obsolete builds through app maintenance. App state and external side effects are not rolled back. Keep patched operations compatible and avoid destructive migrations.

## Verification

Unit tests reject altered metadata and wrong signing keys. Android instrumentation tests exercise actual DEX loading in a non-debuggable app, restart persistence, unconfirmed-boot rollback, tampered DEX/manifest rejection, base-version changes, HTTP rejection, and main-thread protection.

Instrumentation fixtures are signed using the demo key and embedded only in the test APK. Matching fixtures are checked in at `demo/src/androidTest/assets/`. Do not regenerate the demo public key without regenerating these fixtures. `PatchRelayTest` runs offline; `HostedPatchTest` additionally downloads the public demo release.

## Roadmap

1. Prove Android prepared-code patching and rollback on release builds.
2. Add build-time Java/Kotlin instrumentation only after defining compatible changes.
3. Add React Native bundle integration under the same manifest/signature protocol.
4. Research iOS and Flutter runtimes separately; no promise of unrestricted native patching or store acceptance.
