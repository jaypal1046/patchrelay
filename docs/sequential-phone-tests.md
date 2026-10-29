# One installed app, sequential GitHub patches

Use only `dev.patchrelay.demo`: base v0.3.0, version code 3, base compatibility ID 3. Download and verify one patch at a time. Do not install comparison apps or replace the APK between cases.

## Patch 1 — Kotlin logic change

The user confirmed the initial update worked. Before starting patch 2, the phone's native UI was inspected and showed `Patched Kotlin logic: hello PatchRelay` and `Active patch: 1`.

## Patch 2 — add a dependency

Verified on the physical RMX2117 phone on 29 October 2026. Release: [demo-patch-2](https://github.com/jaypal1046/patchrelay/releases/tag/demo-patch-2).

- The installed base APK was copied from the phone and all its DEX files inspected. `org.apache.commons.codec.digest.DigestUtils` was absent.
- `DependencyPatch.kt` calls Commons Codec 1.15 to compute SHA-256 from the supplied input. Packaging includes that library's classes and only the new patch implementation; no Gson upgrade or asset change is attempted.
- Two JVM tests passed: a known `hello` hash and rejection of unsupported operations.
- Only DEX, its signed manifest, and dependency notices were published. No APK was built or installed as part of this test.
- The existing app's Check for update button was used against GitHub's latest-release manifest. A subsequent native-screen inspection showed the updated result and active patch 2. No remote restart command was issued in this test.

Actual phone output:

```text
Patch 2: new dependency
Apache Commons Codec
Input: PatchRelay
SHA-256: f2ae0dc66d8f68bb6c45240ed32b2f553494f583d0e15f0994a8a6880692a583
Active patch: 2
```

The hash matched an independent .NET SHA-256 calculation. The installed APK was copied again after activation; its SHA-256 was unchanged:

```text
abb86033e76b5dacc9b365f0dab2f77c82d4d2c1ac5a8683863830423ce8f297
```

Version code, version name, first-install timestamp and last-update timestamp were unchanged. Package listing showed only `dev.patchrelay.demo` for PatchRelay. Local screenshot and captured evidence are under `.tools/dependency-patch-phone/`; no phone screenshot was uploaded publicly.

**Result: a newly bundled dependency worked in the same installed app after a real GitHub patch download.** This does not demonstrate replacing an existing dependency or updating APK assets. That test stopped after patch 2; patch 3 below was a subsequent user-requested step.

## Patch 3 — R8/ProGuard processing of downloaded code

Verified on the same physical phone and installed APK on 29 October 2026. Release: [demo-patch-3](https://github.com/jaypal1046/patchrelay/releases/tag/demo-patch-3).

- Confirmed active patch 2 before starting. No app was installed or replaced.
- Two new JVM tests checked the formatter's known output and rejection of unsupported operations.
- R8 compiled the patch with `patch/r8-patch-rules.pro`. Rules preserve the reflection entry point, constructor and `invoke` method, while permitting the retained helper class to be renamed.
- Mapping showed `dev.patchrelay.demo.patch.PatchFormatter -> a.a`. The produced DEX contained the renamed descriptor, lacked the original helper descriptor, and retained the named entry point.
- Published signed DEX, manifest and mapping only. An unused passthrough JSON resource was removed during release preparation; the signed DEX was unchanged. The build script now uses R8's `--no-data-resources` option.
- The existing app downloaded and verified patch 3 from GitHub. After restart, its native screen showed:

```text
Patch 3: R8/ProGuard
Input: PatchRelay
Output: YALERHCTAP
Helper class: a.a
Active patch: 3
```

The output matched an independent uppercase/reverse calculation, and the runtime helper name matched the R8 mapping. APK SHA-256 remained `abb86033e76b5dacc9b365f0dab2f77c82d4d2c1ac5a8683863830423ce8f297`; version and installation timestamps were unchanged. Only `dev.patchrelay.demo` was listed for PatchRelay. Local screen capture and evidence are under `.tools/r8-patch-phone/`.

**Result: the downloaded R8-processed patch ran its obfuscated helper successfully in the same installed app.** This step tests the patch's ProGuard rules, not shrinking/obfuscating the installed base APK. No further case was run in this step.

Rebuild with `tools/build-r8-patch.ps1`; signing requires the matching private key. Output is under `dist/r8-patch-3/`, with the R8 mapping alongside the signed patch.

## Patch 4 — controlled startup failure and rollback

Verified on the same physical phone on 29 October 2026. This intentionally failing fixture is [demo-patch-4](https://github.com/jaypal1046/patchrelay/releases/tag/demo-patch-4), now quarantined as a prerelease. The normal latest-update endpoint was restored to working patch 3 and verified after the test.

- Started with the working patch-3 screen, output `YALERHCTAP`, helper `a.a`.
- Built and signed patch 4 for the same package and base. Its `greet` implementation deliberately throws a caught `IllegalStateException` before the app calls `markHealthy()`. The patch body performs no file writes or other side effects. A JVM test verifies this exact controlled failure.
- The same app fetched and verified patch 4 from GitHub. After it was staged, GitHub's normal channel was returned to patch 3; no replacement APK was published or installed.
- After restart, the phone visibly displayed `Patch 4: controlled startup failure.` and its rollback instruction. This failure was recorded before recovery.
- The user force-stopped and reopened the same app. It returned to `Patch 3: R8/ProGuard`, `Output: YALERHCTAP`, `Helper class: a.a`, `Active patch: 3`.
- No update button was pressed between the controlled failure and recovery. Recovery used the previously cached patch; startup does not fetch a manifest automatically.
- Installed APK SHA-256, version and install timestamps remained unchanged. Only `dev.patchrelay.demo` was listed for PatchRelay. Local evidence is under `.tools/rollback-patch-phone/`.

**Result: unconfirmed-startup rollback worked on the physical phone, from failing patch 4 to previously healthy patch 3, without reinstalling.** This was a controlled, caught startup exception followed by a user restart, not a test of every crash type or failures after startup confirmation. SDK patch files/health state are updated normally; no user-file migration was exercised.

The SDK retains its highest accepted version after rollback. After the original version-4 run, the next version had to be 5 or higher. The deliberately failing version 4 is no longer served by the normal latest-release URL.

### Requested repeat using patch 5

On the user's request to repeat the rollback case, the same deliberate failure was published with signed manifest version 5 and a distinct patch-5 error message. Reusing rejected version 4 would be blocked by the version high-water mark. Source tests passed; the existing app's native UI showed the new update downloaded and verified. Test release 5 was then quarantined as a prerelease and the normal channel restored to patch 3.

The user reported that patch 5 showed the same failure. By the next native-screen inspection, the phone already displayed working patch 3, output `YALERHCTAP`, helper `a.a`. APK bytes and all captured version/install timestamps matched the pre-test values.

Unlike the original patch-4 run, the exact patch-5 failure screen was not independently captured, and no matching controlled-failure message could be retrieved from the device log. Therefore the repeat is recorded as **update staged and recovery state observed; exact failure phase not independently verified**, not as a fully verified repeat pass. The user's report is distinguished from tool-observed evidence.

Local records are under `.tools/rollback-repeat-phone/`. At the end of that repeat, versions 4 and 5 were test-only prereleases and normal updates served patch 3. The next new patch had to use version **6 or higher** after the accepted version-5 attempt. No further patch was deployed in that step.

## Patch 6 — healthy forward update after rollback

The user requested the "6th step". This was initially interpreted as patch version 6, but the user subsequently clarified the original test-case table. **Patch versions are not test-case numbers:** original test 6 is the new-dependency scenario already demonstrated by patch 2.

The additional healthy version-6 update was nevertheless built, published, downloaded and activated in the same installed app. The native phone screen and the user's confirmation showed:

```text
Patch 6: healthy update
Input: PatchRelay
Characters: 10
Reversed: yaleRhctaP
Active patch: 6
```

Two new JVM tests passed. Output matched an independent character-count/reversal check. APK hash, version and install timestamps remained unchanged; the normal latest release is now `demo-patch-6`. No APK was installed. Evidence is under `.tools/recovery-patch-phone/`.

**Verified: healthy version-6 activation after failed versions 4/5.** A further cold restart to test version-6 persistence was not performed; no persistence-pass claim is made. This payload did not add a dependency, change an asset, or modify a file.

## Original test-case numbering

Use this table for subsequent test requests; track signed patch versions separately.

| Test | Scenario | Status in the requested one-installed-app/GitHub flow |
| --- | --- | --- |
| 1 | R8 app | Not checked: the current base APK is not minified. Earlier stress-host checks used a separate test app. |
| 2 | R8 patch | Verified by patch 3 in the same app. |
| 3 | Assets | Current raw-DEX loader does not replace APK assets. |
| 4 | File changes | Earlier signed-fixture checks only; not verified through GitHub in this installed base. |
| 5 | Missing dependency | Earlier signed-fixture checks only; not verified through this flow. |
| 6 | New dependency | Verified by Commons Codec patch 2 in the same app. |
| 7 | Dependency upgrade | Verified on the same phone via patch 7: the bundled newer runtime did not replace the APK's old runtime. See below. |

## Original test 7 — upgrade an APK-embedded dependency

Verified on the RMX2117 phone, 29 October 2026, using the existing installed demo. Transport version: **patch 7**. This is the original dependency-upgrade test, not another intentional startup failure.

The payload bundled Kotlin stdlib **2.0.21**, while the APK contains **1.9.23**. `EmbeddedUpgradePatch` reads `KotlinVersion.CURRENT` and compares the runtime class's loader with the patch class's loader. Requested version and actual runtime version are reported separately.

Before publishing, two isolated JVM classloader checks established:

1. With the host's 1.9.23 runtime in the parent, the new patch reports runtime 1.9.23 from `parent/app`.
2. With the identical patch JAR and SDK interface isolated from that old parent, it reports runtime 2.0.21 from `patch`. This proves the newer library was actually bundled.

The same installed app downloaded signed [patch 7](https://github.com/jaypal1046/patchrelay/releases/tag/demo-patch-7) from GitHub. A USB interruption delayed observation. After reconnection and opening the app, its native screen showed:

```text
Test 7: existing dependency upgrade
Requested Kotlin: 2.0.21
Runtime Kotlin: 1.9.23
Library loader: parent/app
Result: NOT UPGRADED
Active patch: 7
```

APK SHA-256, version and installation timestamps matched the pre-test values. Only `dev.patchrelay.demo` was installed for PatchRelay. Evidence and screenshot are under `.tools/embedded-upgrade-phone/`. An optional desktop HTTP download diagnostic stalled and was stopped; it is not used as evidence of the phone's result.

**Outcome: replacing this dependency already embedded in the APK is unsupported by the current parent-first loader.** Patch 7 itself loaded and ran normally; the negative result is the actual runtime observation, not an intentional exception or a fake version label. This does not rule out adding new libraries or using separately isolated/relocated versions inside patch code, and does not claim every possible dependency-upgrade strategy is impossible.

Reproduce with `tools/build-embedded-upgrade.ps1` and the matching signing key. The JVM checks are in `tools/CheckEmbeddedUpgrade.java`. The new runtime is resolved separately from the compiler's 1.9.23 runtime, so the build does not silently change the base SDK or old fixture dependencies. Official [Kotlin 2.0.21 license](https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/LICENSE.txt) and [notice](https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/NOTICE.txt) accompany the distributed patch.

## Rebuild patch 2

Requires the matching private signing key, JDK 17 and Android SDK 35/build tools 35.0.0:

```powershell
.\tools\build-dependency-patch.ps1 -AndroidSdk $env:ANDROID_HOME
```

Output: `dist/dependency-patch-2/`. The manifest targets `dev.patchrelay.demo`, base 3, patch version 2. Existing manifest files are not overwritten. The old hosted-patch regression test is pinned to the immutable v0.3.0 release so publishing a newer latest patch does not change its expected version-1 fixture.
