# Verification and production readiness

[README](../README.md) · [Integration](integration.md) · [Architecture](architecture.md) · [Release workflow](release-workflow.md)

Updated 2026-10-30. PatchRelay is a dependency prototype with a working example host. It does not claim general native hot-patching, Shorebird feature parity or production readiness.

## Current verified release

| Item | Evidence |
| --- | --- |
| Host | PatchRelay Screens 0.4.0 / code 4, base `native-back-v1` |
| Device | RMX2117, Android API 31 |
| Runtime sources / CI | `b251eb0` / [passing run](https://github.com/jaypal1046/patchrelay/actions/runs/41062087592) |
| Base APK and signed flow | [native-back-v1-patch-1](https://github.com/jaypal1046/patchrelay/releases/tag/native-back-v1-patch-1) |
| Polled channel | [native-back-v1/patch.json](https://github.com/jaypal1046/patchrelay/releases/download/native-back-v1/patch.json) |
| Installed APK SHA-256 | `041a21f002fe9c9416d170bef1d8f03d43331e66bfeee49c9d7a3c2deb9dfd68` |
| Local evidence | Ignored `.tools/back-phone/`: UI snapshots, package metadata, before/after APKs |

One authorized APK update installed native Back integration. After that setup, the signed flow/function program downloaded without another APK install; hash, version and install/update timestamps stayed unchanged through activation and testing.

Verified on the current host:

1. Baseline displayed patch 0, original Kotlin result 103 and native shipping helper 15.
2. Signed GitHub program activated as patch 1 and displayed JS result 300 while the unpatched helper remained native.
3. Cart → Details → Review → Receipt ran through the supported renderer.
4. First Android Back dismissed the keyboard and stayed on Details; IME state was confirmed hidden.
5. Next Back returned Details → Cart; re-entry retained the email.
6. Review → Details retained email and accepted terms.
7. Receipt → Cart returned to the completed cart.
8. Cart root Back delegated to Android; no app UI remained visible.
9. Launcher resume returned to Cart with patch 1 active.

SDK tests cover signature/metadata integrity, host binding, bounded trees, optional Back actions, event queues and integer-function selection/results. Current recorded checks: 11 SDK tests, 2 common Kotlin tests, 6 Python tests, compiled baseline/patched-program checks and Gradle/Python host-fingerprint comparison. CI passed library/APK builds and those configured checks.

Android 13+ platform callback handling compiles but has not been verified on a device. The physical phone proof covers API 31, not every Android/WebView/OEM combination.

## Broader test matrix

The four-page flow is program-managed navigation inside the registered `checkout` screen. It is not automatic Android Navigation/Compose conversion. Some broader flow checks were first recorded on 0.3.0; current Back-specific checks are listed above. Do not treat earlier evidence as proof of every later implementation path.

| Area | Evidence | Remaining limit |
| --- | --- | --- |
| Native fallback without matching patch | Runner unit tests; original 103 observed on baseline | Hooks are explicit and preinstalled |
| Matching JS function + native helper | 300 through JS, 15 through Kotlin on phone | Only callback-based `(Int, Int) -> Int` |
| General objects/strings/native instances/suspend contracts | Not implemented | Needs a designed/tested ABI; cannot advertise general method patching |
| Forward flow and on-screen Back | Compiled checks; phone Cart → Details → Review → Receipt and return path | One registered root and one example graph |
| Shared cart/form data across page changes | Phone retained quantity, email and terms on 0.3.0; current Back retained email/terms | In-memory state only |
| Email and terms validation | Compiled checks and phone rejection/enablement cases | No real account/payment validation service |
| Quantity bounds 1–100 | Compiled checks pass | Both extremes not manually exercised on phone |
| Stale page actions | Compiled checks prevent skipped steps and late edits to Review | Rapid physical tap stress remains untested |
| Duplicate local submission | Ten submit events create one local order in compiled test; phone receipt showed one order | No backend transaction/idempotency proof |
| New-order reset | Phone quantity 1, total 100, blank email, unchecked terms, disabled Review | Durable business-state migration absent |
| Native calls between page events | Phone Review retained route/email/total while runner returned 300/15 | No general service bridge |
| Background/resume | Home → launcher retained Review/data on 0.3.0; current root resume retained patch | Does not prove process-death recovery |
| Android Back | Current 0.4.0 keyboard/page/root checks pass on API 31 | Android 13+ callback/gesture device tests pending |
| Process death / Activity recreation | JS globals have no durable restoration | System Back did not recreate the Activity in recorded test; no data-loss result claimed |
| Keyboard, rotation, fonts, accessibility | Current keyboard-first Back and prior cursor/focus checks pass | Rotation, large fonts, screen readers and full IME matrix pending |
| Signature/hash/app/base/API/screen/function IDs | Automated positive/negative checks pass | Deliberate corrupt-download device tests pending |
| Offline / interrupted downloads | Bounded HTTPS and atomic-file staging implemented | Device fault injection pending |
| Bad startup/event/timeout/low memory | Failure and fallback paths exist | Broad rollback/resource-exhaustion phone proof pending |
| Login, network APIs, database, real payment | No general host-service bridge | Unsupported by this example |
| Java host, R8, external consumer app, multiple OS/OEMs | Not verified by current example | Required compatibility work |
| Public dependency distribution | Local module and AAR build | Maven publication, license/metadata and verified consumer setup pending |

## Historical release record

These immutable releases document earlier proofs; use the current base/channel above for new testing.

| Host | Proof | Release / CI |
| --- | --- | --- |
| `native-screens-v1`, code 1 | Missing Pay button and bad total corrected; repeated restart with same APK | [Release](https://github.com/jaypal1046/patchrelay/releases/tag/native-screens-patch-1), [CI](https://github.com/jaypal1046/patchrelay/actions/runs/41021040971) |
| `native-screens-v2`, 0.2.0/code 2 | Form, checkbox, validation, keyed input/cursor and restart persistence | [Archived detailed record](api-2-phone-verification.md), [CI](https://github.com/jaypal1046/patchrelay/actions/runs/41044535080) |
| `native-runner-v1`, 0.3.0/code 3 | Original Kotlin 103 → JS 300, helper 15; second restart kept binding | [Patch 1](https://github.com/jaypal1046/patchrelay/releases/tag/native-runner-v1-patch-1), [CI](https://github.com/jaypal1046/patchrelay/actions/runs/41056776491) |
| Same 0.3.0 base | Patch 2 added multi-page flow, state/validation/reset and Home/resume; exposed missing system Back forwarding | [Patch 2](https://github.com/jaypal1046/patchrelay/releases/tag/native-runner-v1-patch-2), [CI](https://github.com/jaypal1046/patchrelay/actions/runs/41058407332) |
| `native-back-v1`, 0.4.0/code 4 | Native Back hook added in new APK; downloaded flow then passed page/root/keyboard checks | [Current release](https://github.com/jaypal1046/patchrelay/releases/tag/native-back-v1-patch-1) |

Historical APK SHA-256 values:

| Base | SHA-256 |
| --- | --- |
| Screen v1 | `68cc047cf76612e9aa29a71e50b8a25c7c0dd776e251cd0125e0a7b8c4a3f6a9` |
| Screen v2 | `37d0ca8a0d0ae21aa472fc90d39770b0fbc11c5618321c78a862e7c8e3636ea9` |
| Runner v1 | `fc09c301b52d5371f6674ba48b2f3dcaadfe49e393f5ca36f56a2ea105936c88` |

Local capture folders are ignored and are not distributed with a clean clone. Phone captures can contain private foreground content; they were kept local rather than published. Runtime sources changed between bases, so incompatible native releases required explicit APK updates; only the subsequent compatible program changes were OTA.

## Required before production

1. **Developer integration:** generate/validate supported hooks, define broader typed contracts, test external Kotlin and Java hosts, and package reproducible consumer instructions.
2. **State:** define durable screen/business-state persistence and migrations, then exercise process death, recreation and patch transitions.
3. **Failures:** fault-test downloads, writes, signatures, startup health, event errors, timeouts, low memory and fallback on real devices.
4. **Compatibility:** Android 13+ Back/gestures, multiple Android/WebView/OEM versions, R8, rotation, accessibility and font scaling.
5. **Release operations:** protected signing keys, rotation/revocation design, restricted publishing, staged channels/rollout, telemetry, source-mapped errors and corrective-release procedures.
6. **Dependency publication:** choose licensing/metadata and namespace, configure Maven publication, verify the actual published dependency in independent apps. No public coordinates are promised.
7. **Distribution review:** evaluate the actual SDK and consuming app against their store's current rules.

These are remaining requirements, not features implied by the successful demo.

## Distribution and security

Google Play restricts app self-updates and externally downloaded executable formats such as DEX/JAR/native libraries. Its policy describes a conditional exception for code executed by VMs/interpreters with indirect Android API access, and still holds interpreted runtime code/SDKs to other policy requirements. [Google Play Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).

PatchRelay downloads signed JS and uses installed native widgets through a constrained interpreter boundary. That technical choice, and excluding assets/dependencies from patch scope, **do not establish Play approval**. No Play eligibility decision or approval has been obtained.

The signing utility reads a local Base64-encoded private-key file; Base64 is not encryption. Production key protection and rotation are not implemented. Never put keys/tokens in source, APKs, public releases or documentation. A compromised signing key can authorize malicious supported programs.

The WebView restrictions are defense measures, not proof of complete sandbox isolation. Startup health is limited, business effects are not rolled back, and pointing a channel to an old version cannot override device replay protection. See [architecture](architecture.md#state-health-and-failure) and [corrective releases](release-workflow.md#corrective-releases-and-common-failures).
