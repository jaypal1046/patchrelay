# Developer integration

[README](../README.md) · [Architecture](architecture.md) · [Release workflow](release-workflow.md) · [Verification](production-readiness.md)

This guide describes the **implemented prototype APIs**. There is no published Maven coordinate, automatic compiler plugin, `@Patchable` annotation, `PatchRelay.initialize()` or `showScreen()` convenience API today.

## What developers must change

| Existing code | Integration required |
| --- | --- |
| Screen intended to receive UI patches | Author/migrate its patchable layout and behavior through the supported common Kotlin screen API |
| Native function intended to receive logic patches | Prepare an explicit stable-ID runner call before shipping the APK |
| Application startup | Own one updater, load the bundled/selected program once per process |
| Activity hosting patched content | Create/render/dispose an engine and forward Back |
| Real services: payment, login, database, navigation outside the patch host | Keep installed native implementations; a general host-service bridge is not built |

Already-shipped unintegrated code cannot acquire hooks through a JS download. Kotlin developers still edit Kotlin; compatible module code compiles to JS. Existing Android Compose/Activity implementations are not automatically compatible with Kotlin/JS.

## Use the library locally

Inside this repository, the demo already depends on the SDK:

```groovy
dependencies {
    implementation project(':sdk')
}
```

To experiment in another Android project, build `:sdk:assembleRelease`, copy `sdk/build/outputs/aar/sdk-release.aar` into that project's `libs/` and declare:

```groovy
dependencies {
    implementation files('libs/sdk-release.aar')
}
```

This is a local integration experiment, not a completed external-app compatibility test. A raw AAR has no dependency metadata; the host must provide a compatible Kotlin standard library. A Java-only host has not been verified. Minimum Android API is 26 and a working Android WebView provider is required. No npm runtime packages are needed.

The repository's Gradle/Python builder is still configured around `demo/`, `screens/` and the `PatchRelay:screens` JS export. For an external project, adapt those module paths, app identity, fingerprint generation, output/export names and example-specific behavior checks together. Changing only the GitHub `--repository` option does not integrate a different app.

The SDK manifest declares `android.permission.INTERNET` for native HTTPS downloads. Manifest merging brings it into the host. No package-install permission or APK installer is part of the current updater; it stages JS programs inside app-private storage.

## 1. Configure the native release

The example reads [runtime.json](../runtime.json). It supplies application ID, base version, APK version, screen IDs, channel URL, native input paths and text-normalization rules.

The [demo Gradle build](../demo/build.gradle) generates:

- `BASE_VERSION`: native compatibility identity, currently `native-back-v1`.
- `HOST_SHA256`: declared native source/build contract hash.
- `SCREEN_IDS`: registered root IDs.
- `MANIFEST_URL`: HTTPS signed-channel URL.
- `PATCH_PUBLIC_KEY`: Base64-encoded DER RSA public key from the pinned file.

Use your own app ID, hosting URL and signing keys for your app. Keep private patch signing keys outside source control and outside the APK. APK signing and patch signing are separate mechanisms.

## 2. Own startup in the Application

The actual constructor is:

```kotlin
val updates = ScreenUpdates(
    context = applicationContext,
    baseVersion = BuildConfig.BASE_VERSION,
    publicKey = BuildConfig.PATCH_PUBLIC_KEY,
    screenIds = BuildConfig.SCREEN_IDS.split(',').toSet(),
    hostHash = BuildConfig.HOST_SHA256
)
```

On a worker thread, read the packaged baseline and call `updates.start(bundled)` once. Keep the resulting `ScreenProgram` in application-owned state. Activities, including recreated Activities, reuse that selection; they must not call `start` again.

See [DemoApp.kt](../demo/src/main/java/dev/patchrelay/screens/demo/DemoApp.kt) for the complete implementation. `ScreenProgram.version == 0` means bundled code; positive versions identify downloaded programs.

## 3. Host the native screen

On the UI thread, construct a `ScreenEngine` and a persistent `NativeScreenRenderer` for the selected program:

```kotlin
val renderer = NativeScreenRenderer(activity) { action, value ->
    engine.event(action, value)
}
engine = ScreenEngine(
    context = activity,
    screenId = "checkout",
    onTree = { tree ->
        // Update the same renderer; attach its returned view only when needed.
        showNativeView(renderer.build(tree))
        // Update native Back callback registration from tree.backAction.
    },
    onFailure = { failure ->
        // Close engine, reject program on worker, then display fallback.
    }
)
```

Here `engine` is a host-owned property and `showNativeView` represents the host's view-container code, not an SDK method. Attach `engine.webView` to the Activity as a hidden, inaccessible small view, then call `engine.start(program.script)`. Do not create a fresh renderer for every event; that loses view identity.

For exact attachment, generation checks, first-frame confirmation, fallback and disposal, use [MainActivity.kt](../demo/src/main/java/dev/patchrelay/screens/demo/MainActivity.kt) as the source example rather than treating this fragment as a complete Activity.

## 4. Author supported screen code

The [common module](../screens/src/commonMain/kotlin/Checkout.kt) exports the functions the engine calls. Its helpers are in [Ui.kt](../screens/src/commonMain/kotlin/Ui.kt). For example, inside that module:

```kotlin
return column(
    screenId,
    listOf(
        text("title", "Details", "title"),
        input("email", "Receipt email", email, "email"),
        button("back", "Back to cart", "back_cart")
    ),
    backAction = "back_cart"
)
```

Stable IDs identify views, not source line numbers. `handleEvent` updates supported Kotlin state and returns a complete new tree. Guard transitions by current page so queued stale actions cannot submit or skip a step.

Current capabilities: columns/rows/cards, four text styles, text inputs, checkboxes and enabled buttons. No images, arbitrary Android objects, external navigation or real network/payment calls are exposed by this screen API.

## 5. Forward Android Back

The engine does not intercept Activity events by itself.

- Older Android hosts: call `engine.back()` from the Activity's Back override; call the platform default when it returns false.
- Android 13+ hosts: enable the platform callback in the host manifest and register it while the latest validated tree has a `backAction`. Unregister on root, failure and destruction.
- Existing AndroidX/Compose hosts need their own integration with the Back mechanism they use; that host compatibility has not been verified here.
- Call `engine.close()` when its Activity is destroyed.

The example implements both platform paths. Android's keyboard dismissal was verified before page Back on API 31. See [official Back guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture). Android 13+ callback behavior is compiled but not device-tested.

## 6. Prepare a native function hook

After the engine's initial tree is ready, obtain the runner:

```kotlin
engine.prepareIntFunctions(
    setOf("checkout.total.v1", "checkout.shipping.v1")
) { runner ->
    runner.call("checkout.total.v1", 3, 100, ::originalTotal) { total ->
        resultLabel.text = total.toString()
    }
}
```

The original compiled function remains installed. With no declared patch binding, the runner calls it. A matching binding uses JS instead. The callback may run immediately for native fallback or later for JS; do not assume a synchronous return value.

Invoke engine/runner operations on the UI thread. Native fallback runs on the caller's thread; the runner does not supply a general background executor.

The patch module exports `patchedFunctionIds()` and `invokePatch()`; see [Runner.kt](../screens/src/commonMain/kotlin/Runner.kt). IDs are explicit contracts. Changing a signature is not made compatible by reusing its old ID.

Only two integer arguments and an integer result are implemented. General strings/objects, native handles, suspend functions, automatic method extraction and generated hooks are not implemented. Do not move arbitrary Android code into this module and expect compilation to work.

## 7. Stage updates and handle health

Run `updates.stage(manifestUrl)` on a worker. It returns true for a newly staged version, false for no newer accepted version. It does not replace the running program. Display pending state and activate at the next process start.

After required startup checks succeed, call `updates.markHealthy()`. The demo waits for a valid attached screen and successful prepared-function results. Do not confirm immediately after downloading.

Handle `onFailure`: close the engine; call `updates.reject(bundled, failure)` on a worker; replace the application selection with the fallback and rebuild the host. The demo treats an interpreter timeout as requiring process restart. Consult its recovery code before copying only the happy path.

No scheduler or restart manager is provided. Force-stop/reopen was used for controlled testing, not as a proposed production user experience.

| Updater API | Use |
| --- | --- |
| `start(bundled)` | Worker; once per process; selects bundled/active/pending/fallback program |
| `stage(url)` | Worker; verifies and stages a newer program, without switching the current one |
| `markHealthy()` | After required startup succeeds; use a worker for its storage write |
| `reject(bundled, failure)` | Worker; selects previous/bundled fallback for the host to display |
| `activeVersion()` | Selected session version, 0 for bundled |
| `pendingVersion()` | Staged version, 0 when none |

## What changes need another APK?

A new base is required for new/changed SDK or renderer code, native hooks/capabilities, manifest/permissions, dependency/native-library changes, packaged assets or changed declared native/build inputs. Unsupported original Compose/Activity/native code still follows normal app releases.

Once the needed hooks and renderer capabilities exist in the base, compatible supported screen/flow/function changes can be delivered through signed programs.

Patch selection survives a process restart. Cart/form/page state does not have durable persistence or migration. Real transaction state must not rely on these in-memory demo globals.
