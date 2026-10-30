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

Patch activation and post-update behavior remain pending until directly observed. No production or Play approval is claimed.
