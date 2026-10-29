# Prototype verification — 29 October 2026

Kotlin implementation: Android SDK, demo app, and separately compiled patch.

## Passed

- Local JDK 17 release builds: SDK AAR, non-debuggable demo APK, Kotlin patch JAR and DEX.
- Signature unit test: valid signature accepted; altered payload and wrong key rejected.
- Seven offline instrumentation tests on an Android API 37 emulator: actual DEX loading in a non-debuggable app, healthy restart, unconfirmed-start rollback, repeated rollback to built-in code, tampered DEX/manifest rejection, new base-build isolation, HTTPS enforcement, and main-thread protection.
- GitHub integration instrumentation test: download signed manifest and DEX from v0.2.0, verify, activate Kotlin implementation, reject replay, reject incompatible build.
- Physical RMX2117 phone, Android API 31: base v0.2.0 APK installed and launched. App displayed `Original Kotlin logic: hello PatchRelay` and `Active patch: 0`. Package was non-debuggable.

## Physical-phone test incomplete

Automatic approval review rejected both the combined stop/relaunch command and the subsequent launch-only command that would supply the GitHub manifest URL. No specific reason was supplied. The phone's patch download and activation were not verified; emulator success does not establish physical-device success.

## Reproduce emulator tests

Build `:demo:assembleReleaseAndroidTest`, install the demo APK and its Android test APK on the selected emulator, then run:

```text
adb -s SERIAL shell am instrument -w -r -e class dev.patchrelay.demo.PatchRelayTest dev.patchrelay.demo.test/android.test.InstrumentationTestRunner
adb -s SERIAL shell am instrument -w -r -e class dev.patchrelay.demo.HostedPatchTest dev.patchrelay.demo.test/android.test.InstrumentationTestRunner
```

Expected results: `OK (7 tests)` and `OK (1 test)`. Hosted test requires internet access and a matching latest demo release. Instrumentation tests clear this demo app's patch state; they are not intended for production applications.
