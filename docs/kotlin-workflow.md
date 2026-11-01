# Generated Kotlin workflow

## Existing gap and implemented boundary

Previously `Runner.kt` manually exported a JSON list and a JS switch. The Activity separately implemented fallback math and registered string IDs. Baselines knew source hashes but no function signatures. That demonstrated routing, not a Kotlin developer workflow.

`dev.patchrelay.functions` reads Kotlin declarations through Kotlin compiler PSI (1.9.23), then generates additional Kotlin sources. A bundled JVM IR compiler pass instruments marked suspend declarations: it retains a native body and routes the public entrypoint through the generated suspend bridge. Kotlin/JS compiles the original application body without this interception pass. No source-text rewriting is used.

| Code | Current boundary |
| --- | --- |
| Ordinary logic | Common Kotlin supported by both JVM and JS, compiled from one body; helpers/collections/control flow work within the interpreter's execution limits |
| Routed functions | `@PatchFunction` on public top-level functions with 0..8 explicit `Int`, finite `Double`, `Boolean`, `String` or nullable scalar parameters, with explicit scalar or `Unit` result; suspend declarations supported. No defaults, overloads, receivers, generics, object transports or annotation aliases |
| Android APIs | Cannot be imported into common code; Kotlin compiler rejects them. Native operations must already be registered through `ScreenEngine.registerHostFunction` |
| Compose | Opt-in real Compose compiler/runtime, with PatchRelay native-widget applier; remembered state, derived state, keyed groups, callbacks and synchronous disposal. Android Material/UI, drawing, modifiers, Android Views and asynchronous effects are unsupported |
| Suspend | Generated continuation/Promise transport supports genuine suspension. Native fallback resumes through the engine UI thread. JS calls retain the 10-second timeout. No native coroutine/job objects cross the boundary; awaiting a queued native host response inside the same serialized JS call is unsupported |
| State | Selected scalar variables marked `@PatchState` have generated snapshot/restore and optional explicit schema migration. Unmarked module state resets. No arbitrary native heap/database transfer |
| Navigation | Common Kotlin page/event state plus returned `backAction`; Activity must forward Android Back. New destinations within installed widgets/host capabilities can ship in a full program |
| Platform sources | `src/jvmMain`, `src/jsMain`, `src/androidMain` Kotlin implementations are rejected in this module. Add native capabilities in the host and release a new APK |

The generator scans `src/commonMain/kotlin`. Keep the two screen entry points in the default package. The generated JS `main` registers a stable `globalThis.__patchrelay` API, independent of project/module/output names. The interpreter also accepts the older `PatchRelay:screens` export for legacy prepared programs. The generator owns the shared module's `main`; it rejects a competing application main entrypoint.

## One-time integration

Use this repository's `screens/build.gradle` as the Kotlin multiplatform configuration: JVM + JS IR, executable, plain module. In settings `pluginManagement`, include the local `.patchrelay-tools` build. Add `kotlin.js.ir.output.granularity=whole-program` to `gradle.properties`; the plugin rejects split output because patches must include their complete runtime dependencies. Apply:

```groovy
// shared module
plugins {
    id 'org.jetbrains.kotlin.multiplatform'
    id 'dev.patchrelay.functions'
}
// Android app
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'dev.patchrelay.baseline'
}
dependencies {
    implementation project(':sdk')
    implementation project(':screens')
}
```

The plugin supplies `PatchFunction` and `PatchRelayFunctions` in generated common sources. Import them explicitly when using packages. Do not define a duplicate annotation.

```kotlin
@PatchFunction("checkout.total.v1")
fun checkoutTotal(quantity: Int, price: Int): Int {
    require(quantity in 0..100 && price in 0..1000000)
    return quantity * price
}
```

This one body is used by native fallback, generated JS dispatch and the checkout screen's subtotal calculation. The optional annotation ID is a literal, never an expression. Default is lowercase qualified Kotlin name + `.v1`; moving lines/files or changing parameter names/body preserves it. Renaming/package changes change the default ID. Case collisions, duplicate explicit IDs and duplicate hook names fail generation.

For non-suspend functions, call `PatchRelayFunctions.checkoutTotal(3, 100, runner::call) { ... }`. Omit dispatch to execute compiled native fallback. Direct non-suspend calls retain their original behavior: WebView execution is asynchronous and cannot preserve synchronous return semantics without blocking.

For marked suspend functions, install one dispatcher after `engine.prepareFunctions` and use ordinary Kotlin calls from a coroutine:

```kotlin
engine.prepareFunctions(PatchRelayFunctions.contracts) { runner ->
    PatchRelayFunctions.installDispatcher(runner::callSuspending)
    // From your application's coroutine:
    // val status = checkoutStatus(false)
}
```

The JVM IR pass instruments the declaration, so callers compiled in other native modules and function references reach the same routing entrypoint. With no dispatcher, or an ID absent from the active program, the retained native body runs. The JS build keeps its ordinary implementation, avoiding recursive bridge calls. Clear the dispatcher with `installDispatcher(null)` before disposing/replacing the host. This is one module-wide dispatcher for a single active engine; concurrent independent hosts require separate modules. Call on the engine UI thread. Pending results resume with failure when the engine closes; coroutine cancellation/job propagation is not implemented. Inline declarations are rejected.

Suspend interception is pinned to Kotlin **1.9.23**, JVM main compilations of the shared multiplatform module; no Android-only source conversion or K2 support is claimed. Compiler APIs are [unstable](https://kotlinlang.org/docs/custom-compiler-plugins.html), so compiler upgrades require matching plugin work and verification. The optional Compose backend below compiles for JVM and JS; it does not convert arbitrary Android Compose UI.

Implement `renderAppScreen` and `handleAppEvent` as in `App.kt`. Generated JS wrappers export `renderScreen`, `handleEvent`, `patchedFunctionIds` and `invokePatch`. Optional host-call exports remain the installed capability protocol; the simple demo does not request native host operations.

## Compose runtime backend

Set `patchrelay.compose=true` in `gradle.properties`. The functions plugin adds Compose runtime **1.6.10**, its multiplatform compiler **1.5.13.5**, and a generated `dev.patchrelay.compose` backend. Both targets compile the same application source with the actual Compose compiler. No annotation imitation or source rewriting is used. These versions are pinned to this Kotlin 1.9.23 integration, not a promise of compatibility with newer compilers.

A UI-only module needs no `@PatchFunction` declarations: generation records an empty format-2 function contract, and the SDK rejects any downloaded function binding absent from that contract. Adding native hooks still requires a new APK. `node tools/test-compose-project.mjs` builds an independent Compose-only JVM/JS project and verifies this without dummy functions or handwritten exports.

```kotlin
import androidx.compose.runtime.*
import dev.patchrelay.compose.*

private val screen by lazy {
    RelayComposition("checkout") {
        var count by remember { mutableStateOf(0) }
        Column("counter_panel") {
            Text("counter_value", "Count: $count")
            Button("counter_add", "Add") { count++ }
        }
    }
}
fun renderAppScreen(screenId: String): String = screen.render()
fun handleAppEvent(screenId: String, action: String, value: String?): String = screen.event(action, value)
```

The applier maintains a bounded tree rendered by the existing native widgets. Supported widgets: `Text`, `Button`, `TextField`, `Toggle`, `Column`, `Row`, `Card`. Provide stable widget IDs matching `[a-z][a-z0-9_]{0,23}`; event IDs are generated as `c_` plus the widget ID. Kotlin callbacks contain the business logic/navigation. There is no manually maintained event-dispatch switch for these widgets. The demo combines this backend with existing screens and the installed Back protocol.

Recomposition runs synchronously during explicit render/event transactions, with observed mutable snapshots. `remember`, `mutableStateOf`, `derivedStateOf`, `key` and `DisposableEffect` use the real runtime. Call `dispose()` when permanently discarding a composition. Invalid trees/events dispose that composition and follow normal engine failure handling. Native tree validation, output bounds and sandbox restrictions remain unchanged.

`remember` persists only within one program instance. To retain selected values across process restarts/patch activation, explicitly mirror them into `@PatchState` scalar fields and initialize remembered state from those fields, as `App.kt` and `State.kt` do. Restore snapshots **before first composition**, matching `ScreenEngine.start`. Restoring scalar snapshots does not rewrite already remembered objects, `rememberSaveable`, a navigation controller, native heap or database. No automatic Compose-state migration is claimed.

`LaunchedEffect`, `produceState`, `rememberCoroutineScope` and `snapshotFlow` are rejected, including aliased imports. There is no background recomposition/frame scheduler. Keep asynchronous work in installed host operations and apply responses in explicit application events. Android Material/UI imports are rejected with a backend error. `androidx.compose.ui` widgets/modifiers, graphics, animation, accessibility semantics beyond the installed widgets, `AndroidView`, Android lifecycle/navigation libraries and arbitrary Android APIs require additional backend/native integration. Custom Compose functions can combine the supported widgets and common Kotlin logic; arbitrary Android Compose screens cannot be pasted into this module.

Verify with `:screens:jvmTest`, `node tools/test-compose.mjs` and `node tools/test-interpreter.mjs`. The JS test restores an explicitly saved scalar in a fresh program; browser checks run actual Compose state/navigation inside the restricted worker. The signed workflow fixture changes a Compose callback and screen text in patch 2 alongside earlier function/UI/navigation fixes.

## Baselines and patches

Configure `functionContractFile` in CLI settings and the `patchrelayBaseline` extension (default `screens/build/generated/patchrelay/functions.json`). Configure module-specific `programFile`, `compileTasks`, `screenInputs` and native input paths consistently. Include plugin sources/build files and SDK/host configuration in native inputs; the demo runtime does so. Only shared application source bodies are patch inputs.

`assembleRelease` or `bundleRelease` runs generation/tests/JS compilation before asset merge, bundles JS, calculates the native fingerprint and records original artifact, JS, source ZIP and `baseline.json`. The baseline includes sorted IDs, exact scalar/suspend signatures and the snapshot schema. Format-2 function contracts distinguish the scalar transport from the original integer-only format. The record is immutable and must be backed up privately.

Use the [README commands](../README.md#run-locally) with an explicitly selected baseline. CLI compares native inputs before compilation, verifies sources remain stable during compilation and compares generated function contracts before signing. Added/removed IDs require a new native release even if an old hook might otherwise fall back. Unsupported signatures fail generation; supported signature changes still require a new APK because installed hooks have exact types. malformed/changed baseline signatures also fail compatibility checks. Old baselines without contracts require a new native build. A generated bridge cannot authorize an ID absent from the installed app.

Each patch compiles the **whole** current shared module. Keep earlier fixes in source; patch N is cumulative, never a delta applied to N-1. Runtime still validates registered IDs, return values and signed app/base/fingerprint routes. Runtime failure follows recovery rather than silently executing potentially duplicated side effects.

A later native APK contains its own fallback bodies and matching bundled program. A changed bundle affects the host fingerprint. Program snapshots are base-scoped; business storage stays owned by the native host. SDK upgrade tests reject old-base patches and reset selection/version history without clearing business data. Hosting can retain old and new routes in signed catalogs; always supply the previous catalog when extending it.

Private signing keys are developer-owned, outside distributable packages. Baselines/source ZIPs and mapping files stay private. No upload/install occurs during build, patch or catalog generation. No Google Play approval is claimed.

## Verification scope

`node tools/test-kotlin-workflow.mjs BASELINE_JSON PATCH_DIRECTORY` verifies an already built, CLI-signed simple-pages patch against the selected immutable baseline and app-pinned public key. It checks artifact and native hashes, exact generated contracts, the unchanged bundled program, all baseline/patched pages, rewards and state restored into a fresh patched program. It performs no signing or project edits. The [CLI-only walkthrough](simple-pages-verification.md) records build, comparison, signing and phone activation commands. Earlier isolated-fixture evidence remains historical in the production-readiness report.

Run generator JUnit tests, CLI tests, shared JVM tests, SDK debug tests, demo debug recovery tests and normal APK baseline verification as listed in README. Shared JVM tests exercise ordinary suspend calls from a separate test compilation, delayed patched results, failure and dispatcher removal. Release recovery tests resolve callback field names through the private local R8 mapping; no mapping is packaged. `tools/test-foreign-module.mjs` compiles an unrelated project and verifies package-qualified declarations, recursive native suspend fallback, direct interception, real suspend/resume, Unit/null/scalar values, snapshots and explicit schema migration. Arbitrary synchronous interception and native state migration remain outside support.

## State snapshots and migration

Mark only selected top-level mutable common scalars with explicit types:

```kotlin
@PatchState("cart.quantity")
internal var quantity: Int = 1
```

The generator records stable IDs/types and hashes the schema. `exportState` returns at most 8192 characters; up to 32 values are allowed. `restoreState` decodes all fields before assigning any, so malformed input cannot partially modify state. Same-schema snapshots restore across cumulative patches. A changed schema requires an explicit common Kotlin migration:

```kotlin
import kotlinx.serialization.json.*
fun migrateAppState(schema: String, values: JsonObject): JsonObject {
    require(schema == "YOUR_PREVIOUS_64_CHARACTER_SCHEMA_HASH")
    return JsonObject(mapOf("cart.quantity" to values.getValue("old_quantity")))
}
```

Return every current state field; validate business invariants in migration/application code. Types, required keys, string sizes and total snapshot size remain bounded. Unknown schemas without a migration return `incompatible`; a native host must not overwrite existing data with defaults after such a result. `ScreenEngine` requires successful restoration before rendering.

`ScreenStateStore(context, baseVersion, hostSha256)` stores these JSON values atomically in no-backup app storage. It isolates app/base/fingerprint and rejects malformed or oversized snapshots. Read with `load()`, pass the result to `engine.start(program, savedState)`, and save snapshots from `engine.saveState { store.save(it) }` after healthy rendering. The demo serializes writes on a worker. Persistence applies once the write completes; abrupt termination before that can lose the newest transient edit.

This is explicit program-state persistence and migration within a compatible native base. A new APK starts with its own base-scoped program state. Android objects, native database migrations, account authorization and business storage remain host responsibilities.

## Runtime verification

`node tools/test-interpreter.mjs` exercises the actual Chromium worker/CSP boundary, including generated scalar/suspend calls, delayed/rejected promises, malformed/oversized output, forbidden network/storage/native APIs and worker recovery.

For Android WebView evidence, run an emulator, build `:sdk:assembleDebugAndroidTest`, then run `tools/test-emulator.ps1 -Serial emulator-5580`. This script checks both the emulator-only serial and `ro.kernel.qemu` before installation; it never selects a physical phone. Instrumentation uses the current compiled program and checks screen/navigation/state restore, scalar/null/suspend exports, Promise timeout/failure, installed host calls and the existing sandbox/recovery probes. Emulator evidence is not physical-device or Google Play delivery approval.

### Worker source lifetime

The control page retains its worker Blob URL until worker termination, then revokes it on normal stop or transport failure. Immediate revocation raced asynchronous script loading on WebView 83.0.4103.106. The full API-30 emulator suite passes after this fix; browser checks also assert that URLs remain live through startup and are released on stop/failure. This follows the [object-URL lifetime contract](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static).

For a quick emulator worker/bundled-program check, use `tools/test-emulator.ps1 -Serial emulator-5580 -SmokeOnly`; omit `-SmokeOnly` for the complete security/recovery suite. Neither mode accepts a physical-phone serial. The worker bootstrap is bundled in the SDK, so changing it requires a new APK baseline rather than a downloaded-program patch.

### Release app journey

Build the initial v1 `:demo:assembleRelease :demo:assembleReleaseAndroidTest`, then run `tools/test-emulator.ps1 -Serial emulator-5580 -DemoWorkflow`. This installs only on a verified emulator. It exercises the release Activity, generated function dispatch and Home/Catalog/Cart/Profile pages. It verifies saved name/quantity, force-stops the process and checks restoration. No engine callbacks are injected. For the downloaded v2 program, use `-Edition patch -Version 1` with the existing matching v1 APK. Do not assemble a new APK from patched sources when testing OTA compatibility; see the [phone walkthrough](simple-pages-verification.md).

The test companion exposes `generated_workflow`, `generated_restore`, pinned-key USB staging and tamper/replay checks. SDK and release/debug host tests cover recovery separately. USB staging is a test-only byte transport through normal SDK verification; it is not hosted HTTPS delivery.
