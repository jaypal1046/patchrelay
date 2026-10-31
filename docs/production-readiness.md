# Verification and production readiness

[README](../README.md) · [Integration](integration.md) · [Architecture](architecture.md) · [Release workflow](release-workflow.md)

Updated 2026-10-31. PatchRelay is a dependency prototype with a working example host. It does not claim general native hot-patching, Shorebird feature parity or production readiness.

The [Gradle baseline plugin](gradle-integration.md) now passed actual local
`assembleRelease` and `bundleRelease` builds, archive/fingerprint checks, repeat
record reuse and rejection of an injected mismatched bundled program. CLI alpha03
passes 8 tests, including resolving generated host constants and preserving
unrelated build-plugin directories during init. The new APK/AAB records are
private local outputs, not uploads or phone installation evidence. The legacy
tracked demo snapshot is retained separately and is not automatically selected.

CLI alpha04 also fixes discovery of nested APK/AAB baseline paths in the manual
selection prompt. All 9 CLI tests pass; the updated package is installed locally.

## Security changes in working source

Working source targets unpublished base **0.8.0 / `native-store-v1`**, adding failed-state-commit restoration to the earlier sandbox/API/recovery hardening. The phone's main checkout remains the tested 0.7.0 build; storage changes were tested in the isolated SDK package without an Activity. The published 0.4.0 host below is historical evidence.

`tools/test-interpreter.mjs` runs the actual installed interpreter HTML in Chromium: compiled Kotlin execution, absent DOM/native bridges/WebRTC, denied IndexedDB, denied HTTPS/file/content fetch/WebSocket/remote scripts, malformed/oversized replies, and infinite-loop termination followed by a fresh worker. This is browser evidence, not all-provider Android proof. `SandboxInstrumentation` runs the corresponding core checks against a connected device's real WebView.

On 2026-10-31, the isolated SDK test package passed those core Android probes on RMX2117 / API 31 with `com.google.android.webview` **157.0.8103.14**, targeting API 35. It ran the bundled Kotlin program, confirmed missing native/DOM bridges and denied storage/network/script requests, handled a thrown startup error, timed out an infinite loop, then successfully ran the bundled program in a replacement interpreter. An initial detached-WebView test failed with a stopped renderer; the recorded pass used an attached Activity, matching the host lifecycle. This was an SDK instrumentation test, not installation or OTA-flow verification of the new 0.5.0 demo base.

Current automated SDK count: **34 tests**, including 15 updater recovery/security/routing tests, a Node-to-Android signed-catalog test, 2 pre-parser nesting tests and 5 host-operation tests. Host tests cover allowed names, monotonic IDs, argument/capacity bounds, event ordering, background execution, sanitized failures, deadlines, late-result suppression and queued retries behind native calls that ignore interruption. Version-bound startup confirmation also rejects a late success callback after fallback selection. Historical 0.4.0 counts below describe the earlier release.

Signed catalog routing and Play/native upgrade unit checks now pass: exact-base
selection, direct patch-7 staging without intermediate downloads, wrong app/channel,
tampering, duplicate routes, malformed versions and catalog/manifest mismatch.
Upgrade tests cover both changed base labels and a reused label with a new native
fingerprint, reset to bundled code, fresh-base patch 1 after old patch 7, failed
startup fallback and preserved business preferences. These are Robolectric/API 28
results, not actual Play Store installation/device proof. The new catalog host
checks silently once per process after healthy rendering; it has not been freshly
installed/tested on the phone. Regenerate its unpublished base record first.

CLI alpha02 passes 7 tests, including real Gradle/JAR orchestration on a synthetic
project, signed catalog authoring, local monotonic version reservations and
recovery preserving current edits. This is not a universal native-screen compiler
or remote publishing service. See [update routing](update-routing.md).

Two additional demo UI tests verify silent recovery and first-display gating without opening the phone's instrumentation Activity. They inject actual engine callbacks into the real host Activity under Robolectric: an error restores bundled selection without exposing diagnostic strings, and an unconfirmed candidate tree remains detached. Robolectric does not execute WebView JS; the separate real-phone presentation checks below provide the device proof.

The API instrumentation test also passed on the same phone/provider: JS requested `github.repository.v1`, a native SDK worker made an actual unauthenticated HTTPS GET for `JetBrains/kotlin`, its result returned to JS, and JS produced a validated native screen tree containing `JetBrains/kotlin`. A second program requested `android.reflect`; it was rejected before any registered native handler executed. The initial attempt against the now-private project repository returned 404 and surfaced a sanitized failure; it was not counted as a successful API call. This test installs only the isolated SDK test package; it does not prove OTA activation or UI interaction in the new 0.6.0 demo APK.

The same isolated phone test now exercises the updater with ephemeral signing keys and real Android private storage. It stages a good signed program, then a signed program that throws during actual JS startup; the host rejects it, loads the previous program and successfully executes that fallback. Re-serving the failed version is refused. Injected interrupted transport and corrupt manifest bytes preserve the selected program; saved-script tampering triggers verified fallback. Disable survives a fresh updater instance, prevents even transport invocation, and preserves the high-water mark so only a newer version resumes after re-enable.

An unconfirmed-start test also verifies persisted boot-marker fallback using successive updater instances. This simulates the next process reading its state; it does **not** kill the app process, induce an OS crash loop, fill the physical disk or interrupt real Wi-Fi/USB traffic. Those remain separate device tests. A separate API-29+ test **did terminate the real WebView renderer** on the API-31 phone, observed `onRenderProcessGone` failure handling, disposed the dead view and successfully executed the bundled program in a replacement interpreter. The main application process stayed alive.

The two-thread native executor now has a four-task bounded queue and removes cancelled queued work, preventing unlimited queued retries behind calls which ignore interruption. Limits are per engine; SDK cancellation still cannot forcibly stop arbitrary native code or undo a remote write.

Untrusted JSON is checked for length and nesting before Android's recursive parser runs. This covers manifests, worker responses, screen trees and function-ID lists; a deeply nested program result must be rejected rather than overflow the native thread stack.

Ten updater fault tests run Android storage behavior through test-only Robolectric/API 28: next-session activation, replay refusal, repeated unhealthy boots, failed fallback, saved-code corruption, interrupted atomic writes, interrupted/offline/altered downloads, obstructed storage, persistent disable/re-enable, trust changes and invalid numeric versions. Storage obstruction exercises write failure; it does not simulate a physically full disk or every preferences-commit failure.

Remaining security limits: no hard memory/CPU quota, no proof against WebView vulnerabilities, no independent emergency revocation channel, no production key vault, no rollback of business effects, and no automatic detection of all crashes after `markHealthy`. Removing keys from new host releases does not revoke them in older installed APKs.

## Background storage and transport fault checks (0.8.0 SDK source)

A failed preferences commit could leave new in-memory pending/highest markers even after staging deleted the associated files. The SDK now snapshots prior state and restores it on a failed/throwing commit. A regression test covers a reported failure after memory/disk mutation, a failed rollback report, a thrown commit error and preservation of all supported preference types. The same patch version can retry afterward rather than being blocked by a phantom replay floor. Restoration is best effort; a permanently broken disk still reports failure.

On RMX2117 / API 31, the isolated SDK test package passed the following without launching an Activity:

- Reported failed commits, including a reported failed rollback, preserve the earlier pending version and permit retry.
- Removing owner-write permission from a test-only patch directory causes a real write failure while preserving pending state; restoring permission permits retry.
- An actual TLS response from the public GitHub API is read for 32 bytes, then a test stream injects an IOException. The transport closes/disconnects, and a subsequent full request returns the expected repository.

The transport test interrupts the SDK read; it is not a physical Wi-Fi loss or a remote server disconnect. The phone was not filled with data. Full-disk ENOSPC behavior, OS power loss and every preferences durability failure remain unverified. The tests use only test-package storage/ephemeral keys, not main-app data or developer signing keys. A test flag initially arrived as a string while the harness expected a Boolean, causing the old foreground suite to run; the parser was corrected before recording the background-only pass.

The local 0.8.0 base/AAR builds; the user's main checkout was not updated from 0.7.0 in this round. No source or release was pushed.

## Local 0.7.0 adversarial phone checks

The same checkout app runs the final local **0.7.0 / code 7** build, on RMX2117 / API 31. Installed release APK SHA-256: `64306d51c1d87e71000cc439f5058c7cfe2bfe211026906ed51f68712b7d44cd`. The app APK stayed unchanged between all eight signed-fixture cases:

| Case | Result |
| --- | --- |
| Startup exception | Silent bundled fallback; checkout actions work |
| Rejected candidate before display | Candidate never drawn; checkout actions work |
| Failure after Continue | Silent bundled fallback; checkout actions work |
| Raw transport/browser API recovery through global/prototype descriptors | Required forbidden APIs unavailable; silent fallback remains usable |
| 8 MiB rendered output | Rejected; silent fallback remains usable |
| Synthetic worker message requesting an infinite loop | Forged request ignored; valid Continue flow still runs before deliberate failure/fallback |
| Asynchronous failure after startup | Execution stops; the next app action detects failure and silently falls back |
| Approved native API | Real GitHub HTTPS result reaches the native screen; subsequent deliberate failure still falls back |

Every case subsequently performs Add item → Pay 200 → Payment confirmed: 200 in the bundled checkout. State/preferences are restored after the harness; fixtures use ephemeral keys and isolated private artifact paths, not production signing keys or GitHub delivery. This targets the real release Activity and does not open the separate SDK security-check screen. Local evidence stays in ignored `.tools/guard-phone/`, including `installed-base-final.apk` and `normal-checkout-final.xml`.

Real Chromium tests additionally confirm normal compiled Kotlin execution, an 8 MiB return followed by a responsive interpreter, 1,000 denied raw-message attempts, inaccessible prototype transport references/nested-worker APIs, immutable API bindings, forged-event rejection, unhandled async exception/rejection handling (including a tampered event method) and infinite-loop termination/replacement. Removed network APIs are tested independently from native approved-operation support.

The oversized-output test is bounded: **it does not establish a hard heap limit or prove resistance to renderer/OS memory exhaustion**. Browser capability removal is defense in depth, not a formal sandbox audit. Supported programs must use declared exports/native hooks rather than browser APIs. A host must still validate and rate-limit approved operations. Broader Android/provider/OEM coverage, independent key revocation and durable user-state restoration remain pending.

## Local 0.6.0 checkout phone test

The real `PatchRelay Screens` app was updated once from 0.4.0 to the local release build **0.6.0 / code 6**, on RMX2117 / API 31. APK SHA-256: `3f49d29ee2ed1996d77543b87a2fb5b2641c2d613eb422fa0846f7cbb2ef9f0c`.

`SilentRecoveryInstrumentation` targets the actual release `MainActivity`, not the separate SDK security-check Activity. It uses ephemeral signing keys and an isolated artifact directory, verifies signed fixtures through the updater, and restores the original updater, selection and complete SDK-preferences snapshot after testing. The app APK was not reinstalled between cases:

| Case | Device result |
| --- | --- |
| Signed program throws during startup | Silent bundled fallback; Add item and Pay work |
| Valid candidate tree fails prepared-function validation | Rejected candidate is never drawn; silent bundled fallback remains usable |
| Program passes startup then throws after Continue | Silent bundled fallback; Add item → Pay 200 → Payment confirmed: 200 |

Each case verifies usable fallback, not just an absence of errors. A pre-draw observer rejects private diagnostic text and the rejected candidate marker on visible frames. All three cases passed. The first harness attempt started before `Application.onCreate` initialized its future and crashed; waiting for main-thread initialization fixed the test setup. No failed harness run is counted as a pass.

After restoring state, normal app launch showed Checkout, Items: 1, Total: 100, Payment ready and Pay 100, with no security-check screen or patch-error message. Installed APK hash still matched the build above. Local evidence: ignored `.tools/silent-phone/normal-checkout.xml` and `.tools/silent-phone/installed-base.apk`.

These were locally injected, signed test fixtures. They do not prove private GitHub downloads, background scheduling, arbitrary Compose patches, durable user-state restoration or real payment processing. No source or artifact was pushed/published.

## Last published release (historical)

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
| Offline / interrupted downloads | Local tests and phone-injected transport failure preserve active code; phone rejects corrupt manifests and saved scripts | Physical network loss, full-disk ENOSPC and power-loss durability remain pending; reported commit/write failures now have background phone coverage |
| Bad startup/event/timeout/low memory | Phone signed-startup failure → verified previous program; boot-marker recovery simulation; actual renderer termination and infinite-loop termination/replacement | Main-app process death, later hard crashes and memory exhaustion remain unverified |
| Approved network API | Real HTTPS round trip through a registered native operation; unknown operation denied | Read-only public repository test; no private-hosting/authentication or real transaction proof |
| Login, database, real payment | No automatic integration | Needs native authorization, consent, schemas and idempotency |
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

The signing utility reads a local Base64-encoded private-key file; Base64 is not encryption. Production key protection is not implemented. Host-owned overlapping keys support rotation, but revocation in already installed older hosts needs a separate trusted mechanism or host update. Never put keys/tokens in source, APKs, public releases or documentation. A compromised signing key can authorize malicious supported programs in apps trusting that key.

Developers remain responsible for every patch: preserve disclosed data practices, permissions and legitimate user flows; do not use patches for deceptive payments, undisclosed data collection or policy evasion. Sign only reviewed programs. Sandbox restrictions do not make malicious business behavior acceptable.

The WebView restrictions are defense measures, not proof of complete sandbox isolation. Startup health is limited, business effects are not rolled back, and pointing a channel to an old version cannot override device replay protection. See [architecture](architecture.md#state-health-and-failure) and [corrective releases](release-workflow.md#corrective-releases-and-common-failures).
