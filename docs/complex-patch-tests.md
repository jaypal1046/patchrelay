# Complex Kotlin patch tests

Tested 29 October 2026 on an Android API 37 emulator, in a non-debuggable, R8-minified app (`dev.patchrelay.demo.stress`). The working phone demo and its GitHub release were not replaced. These are offline, signed-fixture tests of the current loader, not a new production release.

| Scenario | Observed result |
| --- | --- |
| Kotlin logic in R8-minified host | Works after fixing SDK consumer keep rules. Host obfuscation verified from R8 mapping. |
| R8-processed patch | Loads and invokes successfully with its reflection entry-point class, constructor, and interface method preserved. |
| Changed JSON asset inside patch JAR | Does not reach runtime. JAR resources are absent from raw DEX; original `assets/catalog.json` in the APK remains revision 1. |
| App-private file contents | Patch writes revision 2 successfully. Code rollback restores built-in logic but leaves the file at revision 2. |
| New dependency omitted from patch | Calling Apache Commons Codec fails with the expected missing Codec class. Loading the patch alone does not prove every operation is callable. |
| New dependency bundled into patch | Commons Codec 1.15 computes the expected SHA-256 of `hello`. |
| Existing dependency upgraded in bundle | Patch contains Gson 2.10.1, but parent-first loading resolves host Gson 2.8.9. Calling the newer `disableJdkUnsafe()` API raises `NoSuchMethodError`. |

Result: **7 tests, 0 failures, 0 errors** after the keep-rule fix. Several tests intentionally confirm unsupported behavior; this does not mean assets or existing-library upgrades now work.

## Bug found and fixed

Initial run produced six errors because R8 renamed or removed Kotlin runtime classes, including `kotlin.jvm.internal.Intrinsics`. The SDK's original rules kept only its own public ABI. Downloaded code still referenced original Kotlin runtime class names.

The SDK now keeps Kotlin runtime classes and members stable. This reduces shrinking and can increase APK size. Other host libraries referenced by patches also need an explicit, stable ABI/keep policy. The test host keeps Gson unchanged specifically to isolate dependency-version resolution from shrinking.

The fix is in repository source. Existing v0.3.0 release downloads predate this change; this test run does not replace that release or its SDK AAR.

## Test artifacts

`ComplexPatch.kt` adds real library calls, JSON file writes, and a resource lookup. The patch compiles against Gson 2.10.1 and Commons Codec 1.15; the host contains Gson 2.8.9 and no Codec.

Three signed fixtures use the same patch interface and entry point:

- `thin`: code without dependency classes.
- `bundled`: code plus Gson and Codec classes.
- `minified`: R8-processed code with external dependencies supplied as compilation libraries.

The dependency-bundled packaging is part of this test setup. The normal `build-patch.ps1` publisher still builds a code-only DEX; it does not automatically package arbitrary dependencies or assets. Native libraries, Android AAR resources, manifests/permissions, Kotlin runtime upgrades, image assets, and database migrations were not tested.

Fixtures are embedded only in the stress test APK. Their signed URLs are metadata placeholders under this repository; the tests copy fixtures into app-private storage and do not fetch those URLs. Public download/signature handling was tested separately in the original GitHub integration test.

## Reproduce

Requires JDK 17, Android SDK 35/build tools 35.0.0, and a running emulator. The signing private key must match `demo/patch-public-key.txt` to regenerate fixtures:

```powershell
.\tools\test-complex.ps1 -AndroidSdk $env:ANDROID_HOME -Device emulator-5554
```

This builds/minifies the isolated host and patch variants, signs fixtures, validates host obfuscation, and runs the seven tests. Results: `dist/stress/results.txt`; patch mapping: `dist/stress/minified/mapping.txt`; host mapping: `demo/build/outputs/mapping/stress/mapping.txt`.

With the checked-in fixtures, no private signing key is needed to build and run tests:

```powershell
.\gradlew.bat :demo:assembleStress :demo:assembleStressAndroidTest -PtestBuildType=stress
adb -s emulator-5554 install -r demo/build/outputs/apk/stress/demo-stress.apk
adb -s emulator-5554 install -r demo/build/outputs/apk/androidTest/stress/demo-stress-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class dev.patchrelay.demo.ComplexPatchTest dev.patchrelay.demo.stress.test/android.test.InstrumentationTestRunner
```

The test runner clears only the isolated stress app's patch state and removes its own temporary data file. It never targets the working phone demo. The helper script accepts emulator IDs only.
