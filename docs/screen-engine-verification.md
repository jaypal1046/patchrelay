# Native screen replacement verification

## Local checks

- Kotlin compiler produces a standalone JS program from the supported Kotlin screen module.
- Baseline program: item count changes while total stays 0; payment button is missing.
- Fixed program: correct total, added payment button and working local confirmation event.
- Kotlin/JVM tests cover escaping and unsupported calls.
- SDK tests cover widget validation, signed identity/base/screen/API binding, tampering and UTF-8 validation.
- Python tests cover native source additions/removals/content changes and cross-platform line endings.
- APK ZIP inspection confirms it contains the original baseline program, not the fixed downloaded program.

## Phone baseline observed

RMX2117 (API 31), 2026-10-30: installed the initial PatchRelay Screens APK after explicit approval. Native UI showed screen patch 0. Tapping Add item produced Items: 2 with Total: 0, with no Pay button. The UI hierarchy identifies Android TextView/Button widgets.

APK SHA-256: `68cc047cf76612e9aa29a71e50b8a25c7c0dd776e251cd0125e0a7b8c4a3f6a9`. VersionCode 1, firstInstallTime/lastUpdateTime `2026-10-30 12:53:19`. Phone screenshots/UI dumps are saved locally under `.tools/native-screen-phone/`; they are not uploaded publicly.

## Phone update observed

Published patch 1 and its signed channel manifest. The first request returned HTTP 404 because the versioned artifact release was ready before the dedicated channel release; publishing the channel manifest resolved this without modifying or reinstalling the APK.

After verified download, the user force-stopped and reopened the same app. Native UI showed screen patch 1, a correct total and the restored Pay button. Controlled action: Add item produced Items: 2 and Total: 200; tapping the native Pay 200 button produced Payment confirmed: 200. No transaction or external service was involved.

Before/after APK SHA-256 stayed `68cc047cf76612e9aa29a71e50b8a25c7c0dd776e251cd0125e0a7b8c4a3f6a9`, versionCode stayed 1 and both install timestamps stayed `2026-10-30 12:53:19`. APK ZIP inspection confirms the original program remains packaged; the downloaded fixed program differs. Its SHA-256 is `1ae4d430f28a10a93c3ed0bad4e69f1023f1ad6bdc78ac356951af6933bc173a`.

The user force-stopped/reopened a second time. Patch 1 remained active, Pay 100 remained present and Total: 100 matched the reset demo item count of 1. A further update check returned No newer screen update. Active patch 1. This confirms healthy patch persistence and accepted-version handling for this flow; demo cart state is not persisted.

Only the supported module's Checkout.kt differs from the recorded base source. `demo/fixtures/Checkout.baseline.kt` preserves the original Kotlin file and matches its recorded source hash. The native-source compatibility gate still matches. A command-level negative check also rejected a deliberately incompatible baseline before signing.

Seven Kotlin/JVM unit tests (five SDK, two screen DSL), three Python compatibility tests and compiled-program checks for baseline/fixed behavior pass. GitHub CI passed on runtime commit `670a4f4` ([run](https://github.com/jaypal1046/patchrelay/actions/runs/41021040971)). No production or Play approval is claimed.

## Not yet verified

Bad-program startup rollback, timeout/resource-exhaustion recovery, process death during staging, unavailable/older WebView providers and broad device coverage remain pending. This proof covers the supported custom Kotlin screen API; it does not establish arbitrary Activity/Android Compose conversion or patching of the native renderer itself.
