# Developer integration

[README](../README.md) · [Architecture](architecture.md) · [Release workflow](release-workflow.md) · [Verification](production-readiness.md)

This guide describes the **implemented prototype APIs**. There is no published Maven coordinate, automatic compiler plugin, `@Patchable` annotation, `PatchRelay.initialize()` or `showScreen()` convenience API today.

A [Gradle baseline-recording plugin](gradle-integration.md) is now implemented
for prepared release builds. It compiles/bundles screen code and records APK/AAB
contracts automatically; it is not the arbitrary-Kotlin compiler/hook-generation
plugin mentioned above. This repository's demo applies it already.

## What developers must change

| Existing code | Integration required |
| --- | --- |
| Screen intended to receive UI patches | Author/migrate its patchable layout and behavior through the supported common Kotlin screen API |
| Native function intended to receive logic patches | Prepare an explicit stable-ID runner call before shipping the APK |
| Application startup | Own one updater, load the bundled/selected program once per process |
| Activity hosting patched content | Create/render/dispose an engine and forward Back |
| Existing backend operation | Register a bounded `String → String` host operation before engine startup; validate arguments and keep credentials native |
| Payment, login, database, navigation outside the patch host | Not automatically integrated; native authorization, consent and side-effect handling remain the developer's responsibility |

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

**No separate WebView dependency or manual `WebView(...)` construction is required.** `android.webkit.WebView` is part of Android; `ScreenEngine` creates the hidden interpreter view. Current low-level integration still attaches `engine.webView` to the host, as shown below. Visible content uses `NativeScreenRenderer`, not HTML. The device supplies its WebView provider; Maven does not install that provider. Gradle resolves declared library dependencies and packages the AAR's code/assets/manifest. A [local Maven preview](maven-publishing.md) is configured; public Central publication is pending. [Android WebView](https://developer.android.com/reference/android/webkit/WebView), [library publishing](https://developer.android.com/build/publish-library/upload-library).

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

### Choose your patch host in local.properties

For discovery across multiple native builds, prefer a stable signed catalog:

```properties
patchrelay.catalogUrl=https://updates.your-domain.com/your-app/production.json
patchrelay.channel=production
```

The new demo generates `CATALOG_URL`/`UPDATE_CHANNEL`, checks silently on a worker
once per process after healthy rendering, and stages only the exact compatible
route. Other hosts call `updates.stageCatalog(url, channel)` on their own worker.
The SDK does not impose Android scheduling or perform UI restarts. One catalog
is signed for one app/channel and holds up to 64 exact base/fingerprint routes.
Use separate catalogs/keys for different apps even if their files share a repo.
The existing `stage(manifestUrl)` API remains available for direct single-base
channels. When the demo catalog URL is set, it takes precedence over that mode.

Use `patchrelay catalog` to create/update the signed catalog locally, explicitly
supplying the previous catalog or choosing `new`. Upload immutable artifacts
first; only then replace the stable hosted catalog. See [CLI guide](../cli/README.md).

For legacy direct-manifest mode, the demo accepts this optional entry in ignored `local.properties`:

```properties
patchrelay.manifestUrl=https://updates.your-domain.com/android/patch.json
```

It can point to a GitHub release asset, your HTTPS server, object storage or a CDN.
It must serve the signed manifest, not a repository page, HTML login page or APK.
The manifest's signed artifact URL identifies the actual JavaScript download.
Neither endpoint has to belong to PatchRelay's publisher.

Resolution order: Gradle `-Ppatchrelay.manifestUrl=...` (useful for CI), then
`local.properties`, then `runtime.json`'s demo URL. HTTPS is required; embedded
username/password credentials and fragments are rejected. The SDK itself does
not read local.properties: the host Gradle build generates `MANIFEST_URL` and
the host passes it to `updates.stage(BuildConfig.MANIFEST_URL)`. External apps
must add equivalent build configuration; installing the AAR alone does not do so.

The selected URL is compiled into the APK. Keeping local.properties out of Git
does not hide that URL from app users. Do not put tokens, passwords, expiring
signed links or private keys in it. Private repositories/servers require a
separate authenticated delivery design; the current SDK downloader does not
provide custom authentication headers or GitHub login. A private source repo
can still use public signed patch files; signature verification controls trust,
not who can read the file. Use an accessible, stable HTTPS manifest endpoint.

This override changes the channel endpoint, not app identity, trust keys or the
patch builder's GitHub artifact repository. Configure those independently and
sign each manifest with its actual final artifact URL. Already-installed apps
keep their compiled channel until a native update or a deliberately implemented
host-owned configuration mechanism changes it.

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

## 7. Approved backend calls

Register installed native operations **before** `engine.start(...)`:

```kotlin
engine.registerHostFunction("github.repository.v1") { repository ->
    require(repository == "JetBrains/kotlin")
    // Runs on an SDK worker: call your existing native HTTP client here.
    repositoryApi.fetchSummary(repository)
}
```

`repositoryApi` above represents the consuming app's existing client, not a new SDK API. The demo uses a real unauthenticated GET to GitHub's repository endpoint and returns its name/star count. JS cannot supply a URL or credentials through the request contract. The host operation must validate every argument, enforce destinations and authorization, and return at most 4096 characters. Do not register arbitrary URL fetchers, reflection or file-access dispatchers.

After rendering or handling an event, the SDK reads the optional `takeHostRequests()` export if host functions were registered. It returns a JSON array of `{id, function, argument}`: increasing integer IDs, registered function names, string arguments up to 4096 characters, at most four in-flight calls and two worker threads. The complete batch is validated before any operation executes. Calls time out after 30 seconds; sanitized failures and successful strings return through `handleHostResult(screenId, id, result, error)`, which returns the next validated native screen tree.

`screens/src/commonMain/kotlin/HostCalls.kt` contains the actual example exports. No registered host functions means the engine does not require these exports, preserving existing screen-only programs. New native registrations require a new compatible base. Patches can change supported request timing, arguments and response/UI handling within those installed operations; they cannot rewrite arbitrary existing Retrofit/Kotlin callers or add new native capabilities.

The engine closes outstanding operations when disposed and ignores late completions. Interrupting a worker is **not** a guarantee that a remote request or business transaction was cancelled. There is no automatic retry. Payments/writes need native authorization, user consent and backend idempotency. Tokens stay inside native code and are not returned to the patch.

The four-request limit is for tracked requests. Native handlers that ignore interruption may keep running after timeout. Each engine limits execution to two workers and four queued tasks; cancelled queued tasks are removed, preventing an unbounded retry queue. Configure real network/client deadlines too. Recreating engines does not give the SDK power to forcibly terminate trusted native code.

## 8. Stage updates and handle health

Run `updates.stage(manifestUrl)` on a worker. It returns true for a newly staged version, false for no newer accepted version. It does not replace the running program. Display pending state and activate at the next process start.

After required startup checks succeed, call `updates.markHealthy(program.version)` on a worker. It returns false for an obsolete selection, so an old callback cannot confirm a replacement program during recovery. The no-argument method remains supported. The demo builds the candidate native view offscreen and checks prepared-function results before confirming health and attaching its first visible tree. Do not confirm immediately after downloading.

Handle `onFailure`: close the engine; call `updates.reject(bundled, failure)` on a worker; replace the application selection with the fallback and rebuild the host. Worker timeout terminates patch execution before this callback; the demo starts the fallback immediately. Consult its recovery code before copying only the happy path.

**Keep patch failures out of the app's normal UI.** A failed download leaves the current screen alone. A failed candidate is not attached; recovery tries previous and bundled programs without a patch-error banner, exception text or restart prompt. The demo logs diagnostic details for developers. If persisting rollback fails, it still tries the bundled program in memory. If the bundled app screen itself cannot run, the host displays an ordinary app-level unavailable message; patching cannot repair every original-app failure.

The separate `PatchRelay SDK security checks…` screen belongs only to the instrumentation test fixture. The SDK never launches it during update checks. Existing manual demo controls remain development tools; they are not required user prompts or a background scheduling service.

The actual release host's silent recovery can be tested on a dedicated phone with `:demo:connectedReleaseAndroidTest` (or by installing its release/test APKs and running `SilentRecoveryInstrumentation`). This updates/targets the real demo app. Its eight signed-fixture cases cover startup/display/runtime failures, restricted transport, oversized output, forged events, asynchronous failures and a real approved native API call, then exercise Add item and the local demo Pay action on the fallback. The harness backs up/restores SDK preferences and uses temporary artifact paths; it does not download fixtures from GitHub. Do not run destructive fault scenarios against an app with real payment side effects.

No scheduler or restart manager is provided. Force-stop/reopen was used for controlled testing, not as a proposed production user experience.

SDK storage/transport fault checks can run without opening an Activity: build `:sdk:assembleDebugAndroidTest`, install its test APK on a dedicated test phone, then run `adb shell am instrument -w -e backgroundFaultsOnly true dev.patchrelay.test/dev.patchrelay.screens.SandboxInstrumentation`. This uses only test-package storage and ephemeral keys. It injects commit failures, temporarily removes write permission from its own fixture directory, interrupts an actual HTTPS read after 32 bytes and checks retry. It does not fill device storage or toggle connectivity. Instrumentation command-line extras are strings; the background selector is parsed accordingly.

| Updater API | Use |
| --- | --- |
| `start(bundled)` | Worker; once per process; selects bundled/active/pending/fallback program |
| `stage(url)` | Worker; verifies and stages a newer program, without switching the current one |
| `markHealthy()` | After required startup succeeds; use a worker for its storage write |
| `markHealthy(version)` | Worker; confirms only the selected active version and returns false for stale callbacks |
| `reject(bundled, failure)` | Worker; selects previous/bundled fallback for the host to display |
| `disable(bundled)` | Worker; persist disabled state, clear patch selection, return bundled program; host closes/replaces the old engine |
| `enable()` | Worker; allow staging again, preserving the accepted-version floor |
| `activeVersion()` | Selected session version, 0 for bundled |
| `pendingVersion()` | Staged version, 0 when none |

The existing constructor accepting one public-key string remains supported. For planned rotation, its third argument can instead be a `Set<String>` containing 1–8 trusted public keys. The host owns this set; patches cannot add keys. Build a trusted host release to remove a compromised key. There is no remote revocation service, and users still on an older APK retain its original trust configuration.

Patch programs use the declared exports and host-operation protocol, not browser APIs. Starting with the 0.7.0 base, raw worker messaging, nested workers/channels, direct networking/storage and canvas helpers are sealed off. Console methods are quiet shims; native developer diagnostics still receive interpreter failures. Output limits apply before transfer to the control page. Engine changes need a new base APK and fingerprint; do not send these programs to old incompatible channels.

Call `disable` only from a trusted host control, then replace the running engine with its returned bundled program. Persist the application selection too, as in failure recovery. Calling the updater alone cannot remove a screen already displayed by another engine. `stage` returns false without fetching while disabled. After explicit `enable`, previously accepted versions remain blocked; publish a higher version to resume.

## What changes need another APK?

A new base is required for new/changed SDK or renderer code, native hooks/capabilities, manifest/permissions, dependency/native-library changes, packaged assets or changed declared native/build inputs. Unsupported original Compose/Activity/native code still follows normal app releases.

Once the needed hooks and renderer capabilities exist in the base, compatible supported screen/flow/function changes can be delivered through signed programs.

Patch selection survives a process restart. Cart/form/page state does not have durable persistence or migration. Real transaction state must not rely on these in-memory demo globals.
