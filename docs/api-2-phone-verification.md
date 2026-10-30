# API 2 phone verification

**Historical record: example host 0.2.0, base `native-screens-v2`.** These results and test counts describe that release, not the current 0.4.0 host. They are preserved as evidence, not current installation/publishing instructions.

[README](../README.md) · [Current integration](integration.md) · [Current verification](production-readiness.md) · [Release workflow](release-workflow.md)

Date: 2026-10-30. Device: RMX2117, Android API 31. Commit: `9e5acfd`.

## Artifacts

- [Base APK and patch release](https://github.com/jaypal1046/patchrelay/releases/tag/native-screens-v2-patch-1)
- [API-2 channel](https://github.com/jaypal1046/patchrelay/releases/tag/native-screens-v2)
- [Successful CI](https://github.com/jaypal1046/patchrelay/actions/runs/41044535080)
- Base: `native-screens-v2`, application version 0.2.0 / code 2.
- APK SHA-256: `37d0ca8a0d0ae21aa472fc90d39770b0fbc11c5618321c78a862e7c8e3636ea9`.
- Host contract SHA-256: `76ad0271fb63466c9c4f2282aa8e8da8e42a2af28931eb6ec7d4f6f262031cf6`.

The new renderer required one authorized APK update from v1 to v2. Every step below then ran without another APK install.

## Observed on phone

1. V2 base displayed patch 0: simple checkout, quantity 1, total 100, Add item and Pay.
2. App downloaded and verified the signed patch from the GitHub channel. User force-stopped and reopened.
3. Patch 1 displayed nested card/row, native input, native checkbox, styled validation and disabled Pay.
4. Typing `invalid` kept the input focused; hiding the keyboard exposed “Enter a valid email.” Pay remained disabled.
5. Moved cursor two characters left; inserted `x`, then `y` across separate events. Input became `invalxyid`, still focused. This verifies middle-of-text selection survived updates.
6. Appended `@example.com`. “Email ready.” appeared, but Pay stayed disabled before terms acceptance.
7. Checked terms. Pay became enabled.
8. Added an item. Quantity 2, total 200 and Pay 200 appeared; email remained intact.
9. Tapped Pay. “Payment confirmed: 200” appeared. Quantity and payment buttons became disabled.
10. Pulled installed APK again. Its SHA-256 and install/update timestamps exactly matched before activation.
11. User force-stopped and reopened again. Patch 1 remained active; in-memory quantity/email/terms reset as documented.
12. Another update check reported “No newer screen update. Active patch 1.”

Local evidence is under ignored `.tools/v2-phone/`: UI hierarchies for each step, install metadata, before/after APKs and a confirmation screenshot. Evidence stays local; unrelated foreground app content is not published.

## Automated checks

- SDK: 8 passing tests for signatures, metadata/host binding, strict bounded nested trees and event queue ordering/limits.
- Kotlin common: 2 passing tests.
- Compiled JavaScript: baseline and patched checkout behavior, form validation and unsupported event/value rejection.
- Python: 5 passing compatibility tests.
- APK bundled-program hash matched release snapshot.
- Gradle and Python host fingerprints matched.
- Actual release envelope verified independently with Node's RSA verifier; script hash and compatibility metadata matched.
- CI passed all build/test/host-contract checks.

## Not established

Other Android/WebView/OEM versions, accessibility/font scaling, rotation/recreation, interrupted downloads, late interpreter failures, low-memory recovery and malicious-program isolation were not tested in this pass. No production key management, remote rollout/rollback service or store approval is implied. The checkout performs no real payment.
