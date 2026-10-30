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

No new dependencies, generic native bridge, arbitrary Compose translation, navigation stack or background scheduler were added in this iteration.
