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

**Result: a newly bundled dependency worked in the same installed app after a real GitHub patch download.** This does not demonstrate replacing an existing dependency or updating APK assets. No additional case was run after this result.

## Rebuild patch 2

Requires the matching private signing key, JDK 17 and Android SDK 35/build tools 35.0.0:

```powershell
.\tools\build-dependency-patch.ps1 -AndroidSdk $env:ANDROID_HOME
```

Output: `dist/dependency-patch-2/`. The manifest targets `dev.patchrelay.demo`, base 3, patch version 2. Existing manifest files are not overwritten. The old hosted-patch regression test is pinned to the immutable v0.3.0 release so publishing a newer latest patch does not change its expected version-1 fixture.
