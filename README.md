# PatchRelay

Current demo: a small multi-file Compose app in `screens` with Home, Catalog, Cart and Profile. The signed patch updates those pages, prices, rewards and navigation and adds Offers. Follow the [CLI-only baseline/patch/phone walkthrough](docs/simple-pages-verification.md). The previous demo sources were preserved privately before replacement.

Experimental Android SDK and CLI for signed JavaScript updates to **supported shared Kotlin screens, logic and navigation**. No downloaded DEX/JAR/native libraries. React Native and Flutter are outside scope.

Kotlin/JS compiles the complete shared program. Installed native widgets render its validated screen trees. A build plugin generates function IDs, typed native routing hooks, JS exports and a baseline function contract. Developers maintain one Kotlin implementation, not separate Kotlin/JS bodies.

This is a bounded vertical slice, not arbitrary Kotlin/Compose patching. A Kotlin 1.9.23 JVM IR pass automatically routes marked suspend-function entrypoints after one dispatcher installation; non-suspend calls still use generated callback hooks. New hooks require a new APK. Existing installed builds cannot acquire this integration through a JS patch.

Opt-in Compose support uses the real Compose compiler/runtime with PatchRelay's native-widget backend. Add `patchrelay.compose=true` to `gradle.properties` (enabled here). Write `@Composable` functions with `androidx.compose.runtime` state and `dev.patchrelay.compose` widgets: `Text`, `Button`, `TextField`, `Toggle`, `Column`, `Row`, `Card`. `RelayComposition.render()` emits the installed tree protocol; `event()` executes Kotlin callbacks and recomposes. See [working Compose screen](screens/src/commonMain/kotlin/App.kt) and [Compose boundaries](docs/kotlin-workflow.md#compose-runtime-backend). Android Material/Compose UI, drawing, platform APIs and asynchronous effects are unsupported.

## Supported code

In `screens/src/commonMain/kotlin/Functions.kt`:

```kotlin
@PatchFunction
fun checkoutTotal(quantity: Int, price: Int): Int {
    require(quantity in 0..100 && price in 0..1000000)
    return quantity * price
}
```

The plugin supplies the annotation. The bridge also supports `String`, `Boolean`, finite `Double`, nullable scalar types, zero through eight arguments and `Unit` results. `suspend` declarations use continuation/Promise transport. Default ID: lowercase qualified function name plus `.v1`, independent of file position/body. Optional `@PatchFunction("checkout.total.v1")` preserves a chosen public contract across renames. Duplicate IDs/hook names fail the build.

The installed host uses generated hooks:

```kotlin
engine.prepareFunctions(PatchRelayFunctions.contracts) { runner ->
    PatchRelayFunctions.checkoutTotal(3, 100, runner::call) { result ->
        resultLabel.text = result.toString()
    }
}
```

Opt selected mutable common values into snapshots with `@PatchState`. `ScreenStateStore` persists bounded state per app/base/fingerprint; an optional `migrateAppState` Kotlin function migrates schemas. See [state contract](docs/kotlin-workflow.md#state-snapshots-and-migration).

No handwritten ID list, dispatch switch or duplicate fallback body. Calling a generated hook without a dispatcher uses its original compiled Kotlin body; the runner also uses that body when the active program does not bind that ID. Direct non-suspend calls such as `checkoutTotal` remain direct. For marked suspend functions, install `PatchRelayFunctions.installDispatcher(runner::callSuspending)` once and call `checkoutStatus(false)` normally from a coroutine. Clear it before disposing the host. See [interception constraints](docs/kotlin-workflow.md#one-time-integration).

Screen integration uses the existing Kotlin tree DSL and two application entry points: `renderAppScreen(screenId: String): String` and `handleAppEvent(screenId: String, action: String, value: String?): String`. The plugin generates their JS exports. See [actual application code](screens/src/commonMain/kotlin/App.kt) and [compiler boundary](docs/kotlin-workflow.md).

## Run locally

Requirements: Node 22+, JDK 17+, Android SDK 35. Shared-module builds require `kotlin.js.ir.output.granularity=whole-program` in `gradle.properties` (already set here). Use this checkout's CLI; previously installed/published alpha packages do not contain these new changes. No publication or installation is needed:

```powershell
Set-Location C:\Jay\dev\AutoUpdate
function patchrelay { node C:\Jay\dev\AutoUpdate\cli\bin\patchrelay.mjs @args }
patchrelay --help
.\gradlew.bat :demo:assembleRelease
```

Normal release builds generate hooks, compile/test Kotlin, bundle JS and record immutable APK baselines under `.patchrelay/releases/baselines/`. AAB recording uses `:demo:bundleRelease`. Build commands do not install on a device.

Edit supported Kotlin, keep previous fixes, then explicitly select the released baseline:

```powershell
patchrelay compare --baseline PATH_TO_BASELINE_JSON --diff
patchrelay patch --baseline PATH_TO_BASELINE_JSON --version 1 --url https://updates.example/app/base/1/screens.js
patchrelay catalog --patch PATH_TO_PATCH_JSON --catalog new --channel production --url https://updates.example/app/base/1/patch.json
```

Without flags, `patchrelay patch` prompts for an explicit baseline and version. Each patch contains the whole current program, so users can skip intermediate versions. Changed native inputs, added/removed function IDs or incompatible signatures require a new APK baseline. Pre-generator baselines are rejected for this workflow.

Comparison covers all declared source folders recursively, including hundreds of files. It reports added/modified/removed paths and counts; `--diff` also shows actual source-line changes against the saved release ZIP. It does not build or sign. See [comparison scope](cli/README.md#use).

Use developer-owned keys/HTTPS or GitHub hosting. Configure `signingKey` outside the repository and the matching `publicKeyFile` pinned in the APK. No credentials/private keys belong in packages, APKs or hosted artifacts. Commands build/sign locally; upload only `screens.js`, `patch.json` and signed catalogs yourself. Back up complete baseline directories privately.

Runtime routing retains app ID, channel, native fingerprint and base compatibility. A later APK starts its matching bundled program and rejects old-base patches. Existing signature, hash, replay, HTTPS, bounded-input and recovery checks remain in place. See [routing](docs/update-routing.md) and [integration](docs/integration.md).

## Verify

```powershell
.\gradlew.bat -p .patchrelay-tools test
node --test cli/test/cli.test.mjs
.\gradlew.bat :screens:jvmTest :sdk:testDebugUnitTest :demo:testDebugUnitTest :demo:testReleaseUnitTest :demo:assembleRelease
node tools/test-foreign-module.mjs
node tools/test-compose.mjs screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js patch
node tools/test-compose-project.mjs
node tools/test-kotlin-workflow.mjs .patchrelay/releases/baselines/simple-pages-v1/baseline.json .patchrelay/releases/patches/simple-pages-v1/1
```

For an already-running Android emulator, build `:sdk:assembleDebugAndroidTest`, then run `.\tools\test-emulator.ps1 -Serial emulator-5580`. To exercise the actual release demo, also build `:demo:assembleRelease :demo:assembleReleaseAndroidTest`, then run the helper with `-DemoWorkflow`; it drives catalog/detail/checkout and force-stops/relaunches the app to verify disk state restoration. The script verifies an emulator serial and `ro.kernel.qemu=1` before installing the selected test APKs (plus the release demo in `-DemoWorkflow` mode). It refuses physical-phone serials.

The final command verifies the built patch's pinned-key signature, base and artifact hashes, unchanged bundled fallback, all updated pages, rewards and saved-state transfer into a fresh JS program. It reads existing artifacts and performs no signing, installation or publication. See the walkthrough to create those artifacts first. Do not rebuild the APK while preparing a patch for its existing baseline: release assembly deliberately bundles the current program into a new native base.

Limitations: only public top-level functions with 0..8 bounded scalar arguments and scalar/Unit results, including suspend functions; common Kotlin only; no arbitrary Android APIs/Compose UI conversion or automatic Android heap/database migration or interception beyond marked suspend entrypoints. Screen DSL widgets and installed host operations bound capabilities. JS memory limits and broader device/security validation remain incomplete. Emulator instrumentation passes on API 30 / WebView 83.0.4103.106 and API 37 / WebView 154.0.8037.101. The earlier WebView 83 failure was premature worker Blob-URL revocation; the URL now remains live until worker termination. These are tested configurations, not a guarantee for every provider/version. Current physical-phone Compose verification is recorded in the verification report; these tests do not prove hosted OTA or Play Store delivery. Release recovery tests read the private local R8 mapping; mappings remain outside shipped artifacts.

Current [Google Play policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en) restricts downloaded executable code and describes a conditional interpreter exception. It does not establish PatchRelay approval; interpreted code must still satisfy all applicable policies. Checked 2026-10-31.

[Detailed workflow and compiler boundary](docs/kotlin-workflow.md) · [Gradle setup](docs/gradle-integration.md) · [CLI](cli/README.md) · [Architecture](docs/architecture.md) · [Security/device history](docs/production-readiness.md)

SDK license: [PatchRelay SDK License — Binary Use](LICENSE).
