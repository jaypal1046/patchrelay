# Production readiness

This project follows a release → compatible signed patch → next-launch activation workflow. It does not claim Shorebird feature parity or production readiness.

## Implemented

- Separate native host, supported Kotlin screen code and signing/build tools.
- Central release contract and base-scoped compatibility snapshots.
- Host fingerprint checked by both producer and device.
- Signed manifests, artifact hashes, bounded HTTPS and atomic staging.
- Accepted-version replay protection, unconfirmed-startup fallback.
- Strict bounded UI schema and stable native view identities.
- Ordered/coalesced input events and example form validation.
- JVM, compiled-JavaScript and build-compatibility tests in CI.

## Required before production

1. **Device evidence:** API-2 input/cursor/keyboard behavior, rotation, accessibility/font scaling, offline use, interrupted downloads and fallback on several Android/WebView/OEM versions. V1 phone success does not prove API 2.
2. **Failure testing:** process kill before confirmation, invalid signatures, corrupted storage, bad event results, interpreter timeout and low-memory conditions.
3. **Signing operations:** encrypted or hardware-backed key storage, restricted publishing, rotation/revocation plan and independently reviewed update security.
4. **Release operations:** preview/stable channels, staged rollout, health telemetry, source-mapped errors, controlled rollback/corrective-patch workflow. Local fallback is not a remote rollback service.
5. **State contract:** explicit screen-state persistence/migration before updates affect durable business data or real transactions.
6. **Distribution:** evaluate the actual implementation and app against current store rules. No Play approval has been obtained.

## Verification record

- V1: actual RMX2117/API31 download, activation and second restart; APK unchanged. Historical implementation/evidence remains in Git and the v1 GitHub releases.
- API 2: RMX2117/API31 GitHub download, richer form, invalid/valid email, cursor/focus retention, checkbox gating, quantity, confirmation and second restart passed. APK hash/install timestamps stayed unchanged. See [API-2 phone verification](api-2-phone-verification.md).

## Broader test matrix

The flow patch is a program-managed Cart → Details → Review → Receipt sequence inside the registered `checkout` screen. It does not replace Android Navigation/Activities or automatically rewrite existing Compose pages. Patch 2 changes supported Kotlin only; the installed 0.3.0 APK remains the compatibility baseline.

| Area | Automated evidence | Device evidence / remaining gap |
| --- | --- | --- |
| Original native function without patch | Runner unit test | 103 through Kotlin, earlier base test passed |
| Matching function patch + unpatched helper | Runner and compiled-program checks | 300 through JS, 15 through Kotlin, earlier phone test passed |
| Four-page forward flow | Compiled Kotlin/JS checks pass | Passed Cart → Details → Review → Receipt; total 200, Orders created: 1 |
| On-screen Back and shared cart/form state | Compiled checks pass | Passed: Review → Details → Cart retained email, terms and quantity 2; forward re-entry restored them |
| Required email and terms | Compiled checks pass | Passed: empty/invalid email blocked Review; valid email still required checked terms |
| Quantity bounds 1–100 | Compiled checks pass | Not exercised at both bounds on phone |
| Stale actions cannot skip pages/edit reviewed state | Compiled checks pass | Rapid physical taps not yet checked |
| Local duplicate-submit guard | Ten repeated submit events create one local order | Phone created one receipt; completed Cart blocked further checkout until reset. Real transaction/API idempotency untested |
| New order resets cart/form | Compiled checks pass | Passed: quantity 1, total 100, blank email, unchecked terms and disabled Review |
| Native function calls between page events | Exports and routing tests pass separately | Passed on Review: JS total 300, native shipping 15; route, email and total stayed intact |
| General native function arguments/results | Runner supports only asynchronous `(Int, Int) -> Int` | Objects, general strings, native instances and suspend contracts are not implemented |
| Background/resume | Not automated | Passed: Home → launcher resume preserved Review, quantity 2 and receipt email |
| Android system Back | No host integration exists | Failed page-navigation expectation: Back from Details left the app (0 app UI nodes). Launcher resume returned to Details with draft email retained |
| Process death / Activity recreation | State currently lives in JS memory | Not exercised: system Back did not recreate the Activity in this test. Durable cart/route restoration remains unimplemented |
| Rotation, keyboard, large fonts, accessibility | Input focus tested on previous program | Flow email editing and keyboard dismissal passed; rotation, large fonts and screen-reader coverage pending |
| Signature, hash, wrong host/API/IDs | SDK tests pass | Deliberate corrupted downloads not tested on phone |
| Interrupted download / offline update check | Bounded downloads + atomic staging implemented | Fault-injection tests pending |
| Bad startup / event / timeout rollback | Recovery paths implemented | Broader device failure proof pending |
| Login, network APIs, databases, real payment | No generic host bridge implemented | Unsupported by this proof |
| Java host, R8, Android/WebView/OEM versions | Not covered by current example | Separate compatibility runs required |

Phone run: RMX2117/API31, 2026-10-30, patch 2 from [native-runner-v1-patch-2](https://github.com/jaypal1046/patchrelay/releases/tag/native-runner-v1-patch-2). [CI passed](https://github.com/jaypal1046/patchrelay/actions/runs/41058407332). Before/after installed APK SHA-256 remained `fc09c301b52d5371f6674ba48b2f3dcaadfe49e393f5ca36f56a2ea105936c88`; version 0.3.0/code 3 and install/update timestamps were unchanged. No APK was installed for this flow patch. Local evidence: ignored `.tools/flow-phone/` UI snapshots and package metadata.

Next integration fix: route Android system Back into the program navigation contract. This needs an updated host/SDK base; the current patch cannot intercept an Android event that the installed host never forwards. Durable state restoration and fault tests are separate follow-up work.

No new dependencies, generic native bridge, arbitrary Compose translation, Android navigation integration or background scheduler were added.
