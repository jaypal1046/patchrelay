# PatchRelay Screens

Signed replacement screens for native Android apps, authored through a small Kotlin screen API. Kotlin/JS produces an interpreted program; installed Android widgets render its validated output. No downloaded DEX, React Native or Flutter runtime.

**Experimental.** V1 passed a real-phone download, activation and restart test. API 2 adds richer UI and stronger compatibility checks; its form, input/cursor behavior and restart persistence also passed on that phone. Existing Activity/Android Compose screens must be integrated with this API; arbitrary Kotlin methods are not patchable.

## Components

- `screens/`: screen logic, JSON screen API and common Kotlin tests.
- `sdk/`: downloads, signatures, staging/fallback, interpreter, validation and native rendering.
- `demo/`: native host and bundled baseline program.
- `runtime.json`: app/base identity, channel, screen IDs and native compatibility inputs.
- `tools/screens.py`: release snapshots and compatible patch builds.
- `tools/Publisher.java`: dependency-free RSA signer.

API 2 supports nested columns, rows and cards; styled text; text inputs; checkboxes; enabled/disabled buttons. Stable widget IDs preserve native views, focus and cursor during ordinary updates. Input events queue in order; adjacent changes to the same field are coalesced. Only the latest queued result is rendered.

[Architecture](docs/architecture.md) · [Phone proof](docs/api-2-phone-verification.md) · [Production readiness](docs/production-readiness.md)

## Build and check

Requires JDK 17, Android SDK 35, Python 3, Node 20+ and an Android WebView provider. Gradle, AGP and Kotlin versions are pinned. No npm runtime dependency.

```powershell
.\gradlew.bat :screens:jvmTest :screens:compileProductionExecutableKotlinJs :sdk:testDebugUnitTest :sdk:assembleRelease :demo:assembleRelease
node tools/test-screen-program.mjs demo/src/main/assets/baseline-screen.js baseline
node tools/test-screen-program.mjs screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js fixed
python tools/test_screen_build.py
python tools/screens.py check-host
```

The demonstration APK uses a development signing key. A new base APK is required when installed runtime capabilities change. The Back integration needs version 0.4.0, base `native-back-v1`. Historical screen/runner releases remain unchanged.

## Android Back integration

The validated root tree may declare an optional `backAction`, such as `back_cart` or `back_details`. `ScreenEngine.back()` dispatches it through the ordinary event queue; while an update is pending, repeated Back is consumed. Without a Back action, the host delegates to Android's normal root behavior.

The example host overrides the legacy Back callback and registers an Android 13+ `OnBackInvokedCallback` only for non-root pages. It unregisters on root, failure and Activity disposal. Keyboard dismissal remains handled by Android. The flow declares Details → Cart, Review → Details and Receipt → Cart. Cart declares no handler. See [Android callback guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

This is a new native integration, not a patch for the 0.3.0 host: update the base once, then deliver page/Back-action changes through compatible signed programs.

Verified on RMX2117/API31: first Back dismissed the keyboard without leaving Details; subsequent Back returned Details → Cart. Review → Details retained email/terms, Receipt → Cart worked, and root Back exited normally. Reopening resumed Cart with patch 1 active. After the authorized 0.4.0 setup, the downloaded flow used the same installed APK/hash/timestamps throughout. [Fix release](https://github.com/jaypal1046/patchrelay/releases/tag/native-back-v1-patch-1) · [CI](https://github.com/jaypal1046/patchrelay/actions/runs/41062087592). Android 13+ callback code builds but has not been device-tested.

## Native function runner proof

The original Kotlin implementation remains in the APK. An explicit hook selects it unless a signed program registers a matching stable function ID:

```kotlin
runner.call("checkout.total.v1", 3, 100, ::originalTotal) { result ->
    label.text = result.toString()
}
```

The baseline has no function patches: original Kotlin returns 103. The downloaded program binds `checkout.total.v1` and returns 300. An unpatched shipping helper still runs Kotlin and returns 15. The installed native label displays these results.

This proof supports asynchronous `(Int, Int) -> Int` calls only. Registration IDs are validated against the installed host; results must be 32-bit integers. JS failures reject the program rather than silently re-running original code, which could duplicate side effects.

Hooks and matching exports are handwritten in this proof. Automatic source comparison at function level, compiler-generated hooks, arbitrary objects/Android calls and Compose replacement are not implemented. The screen and function runner share one local interpreter.

Phone verification, 2026-10-30, RMX2117/API31: the base displayed 103 through original Kotlin. After verified GitHub download and user restart, the same native label displayed 300 through JS; the unpatched helper remained 15. Repeat native calls, checkout quantity events and email typing also passed. A second force-stop/reopen kept patch 1 active with 300 through JS and 15 through Kotlin. The APK hash and install/update timestamps stayed unchanged during activation.

APK SHA-256: `fc09c301b52d5371f6674ba48b2f3dcaadfe49e393f5ca36f56a2ea105936c88`. Local evidence: ignored `.tools/runner-phone/`. [Runner release](https://github.com/jaypal1046/patchrelay/releases/tag/native-runner-v1-patch-1) · [Passing CI](https://github.com/jaypal1046/patchrelay/actions/runs/41056776491). SDK tests cover native fallback, matched-ID routing, mixed native/JS functions and invalid IDs/results; compiled-program checks cover the actual Kotlin/JS exports.

Patch 2 adds a program-managed Cart → Details → Review → Receipt flow to the same base. Compiled checks cover page-specific actions, back/forward form state, quantity bounds, required fields, new-order reset and duplicate local submission. Phone checks passed forward/back-button flow, form/cart retention, validation, native calls between page events, Home/resume, receipt and reset. Android system Back left the app instead of navigating to the previous page; launcher resume retained Details and the draft. See the [broader test matrix](docs/production-readiness.md#broader-test-matrix) for evidence and unresolved cases. This is navigation inside one registered screen, not Android Navigation/Compose integration.

## Release workflow

1. Set the new native release identity and channel in `runtime.json`.
2. With baseline screen source in place, build a base:
   `python tools/screens.py base --out dist/native-back-v1-base`.
3. Preserve its APK and `baseline.json`. Install that base once.
4. Change only supported screen Kotlin. Build:
   `python tools/screens.py patch --baseline demo/release-baseline.json --out dist/native-back-v1-patch-1 --version 1 --tag native-back-v1-patch-1`.
5. Upload `screens.js` and `patch.json` to that immutable artifact release.
6. After artifacts are available, publish the signed `patch.json` at the channel URL. Stage in the app; activate on next process start.

Current source contains the rich checkout and function patch. `demo/fixtures/Checkout.v2.baseline.kt` and `demo/fixtures/Runner.baseline.kt` preserve the baseline sources; use both when building a new base. The example's compiled-program checker asserts checkout/runner behavior; change its assertions when intentionally changing that behavior.

Native source, packaged assets, compiler settings and runtime configuration are hashed. Patches must match the base snapshot. The same host fingerprint is embedded in the APK and signed into each patch, preventing a patch built against another host from activating. This fingerprints the declared source/build contract, not the complete APK binary or a reproducible toolchain. APK hashes are recorded separately.

Signing key stays outside Git in ignored `.keys/`; the public key is pinned in the base. Generate your own pair with `java tools/Publisher.java keygen PRIVATE PUBLIC`. Changing the pinned key requires a new base.

## Boundaries

One checkout demonstration, complete-program replacement, in-memory screen state. No automatic Compose conversion, images, arbitrary native API bridge, dependency updates or new permissions. Payment confirmation is a local demonstration; no real transaction occurs.

Signed programs are trusted code. WebView network/file/content access and external navigation are disabled; no JavaScript-to-Java interface is exposed. These restrictions are not proof of complete isolation.

The current demo confirms health after the initial validated tree is attached and both function routes have returned successfully. Startup fallback does not undo persistent data or external side effects, and late failures can still require a corrective release. Interpreter timeouts require process restart.

Store eligibility remains unverified. See the production-readiness checklist before depending on this system for users.
