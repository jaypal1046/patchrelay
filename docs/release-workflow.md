# Release workflow

Use the current checkout's CLI and build plugins. Older alpha packages and Python fixture tools do not implement generated function contracts. [Compiler boundary and supported Kotlin](kotlin-workflow.md) · [SDK lifecycle](integration.md) · [Routing](update-routing.md).

## Configure once

Apply `dev.patchrelay.functions` to the common JVM/JS module and `dev.patchrelay.baseline` to the Android app. Include the plugin build in settings and add the shared module plus SDK as app dependencies. The demo already does this. Use `patchrelay init` in a prepared external project to record module paths, developer hosting and key locations; review/apply its printed hooks. This prototype still requires its documented screen DSL/JS export contract; it does not convert arbitrary Android/Compose code.

Configure the matching `functionContractFile` (default `screens/build/generated/patchrelay/functions.json`) in CLI settings and baseline extension. Mark supported functions with `@PatchFunction`; native callers use generated `PatchRelayFunctions` hooks. Provide `renderAppScreen`/`handleAppEvent`; exports are generated.

Use developer-owned RSA signing keys (minimum 3072 bits). `signingKey` may point outside the repository. `publicKeyFile` contains only the matching public key pinned in the native host. No private key, npm/GitHub credential or authenticated URL belongs in the APK, CLI package or hosted assets. APK signing is separate from patch signing.

```powershell
# Current source command, from this checkout:
function patchrelay { node C:\Jay\dev\AutoUpdate\cli\bin\patchrelay.mjs @args }
patchrelay --help
```

## Release a native base

```powershell
.\gradlew.bat :demo:assembleRelease
# Alternative native bundle artifact:
.\gradlew.bat :demo:bundleRelease
```

Generation, shared Kotlin tests and JS compilation run before asset merge. The build bundles the full program and records an immutable baseline containing original APK/AAB, source ZIP, JS, native/source fingerprints and generated IDs/signatures:

```text
.patchrelay/releases/baselines/<appId>/<variant>/apk-<artifactSha256>/baseline.json
.patchrelay/releases/baselines/<appId>/<variant>/aab-<artifactSha256>/baseline.json
```

Back up the **whole directory privately**. Local build output is not durable backup. Do not publish source ZIPs or private R8 mappings. The demo uses development APK signing; configure production APK signing before distribution. Build commands neither install nor upload.

## Build a cumulative patch

Edit supported common Kotlin, retaining all previous fixes:

```powershell
patchrelay patch --baseline PATH_TO_RELEASED_BASELINE_JSON --version 1 --url https://updates.example/app/base/1/screens.js
```

Without flags, the CLI lists records and asks for an explicit baseline/version. Never infer the target from whichever APK was built most recently. The command rejects native input changes, missing/altered baseline archives, old records without generated contracts, duplicate IDs and added/removed/incompatible hooks. Such changes need a new native baseline.

It compiles/tests the whole current shared module, checks contracts and signs `screens.js` plus `patch.json` under `.patchrelay/releases/patches/<routeHash>/<version>/`. Each program includes all supported screens/state/navigation/logic, so users may skip intermediate patches. Higher versions cannot be reused for the same route, even with a different output directory. Output is local; inspect/test it before hosting.

## Host artifacts and update discovery

```powershell
patchrelay catalog --patch PATH_TO_PATCH_JSON --catalog new --channel production --url https://updates.example/app/base/1/patch.json
```

For every subsequent catalog edit, pass the current signed catalog file instead of `new`; this preserves old-base routes. Upload immutable `screens.js` and `patch.json` first, verify HTTPS accessibility, then replace the stable signed catalog. CLI does not upload anything. GitHub release assets and developer-controlled HTTPS/CDNs can share storage across apps and native bases. Route identity remains app ID/channel/base/native fingerprint.

Configure the installed app's catalog URL/channel in `local.properties` or your native host configuration. URLs must be public or accessible through your own gateway; the SDK does not embed or send GitHub credentials. A GitHub repository page or login page is not an artifact endpoint.

Runtime verifies pinned signatures, exact compatibility, artifact hashes and version/replay rules before staging. Activation occurs on a later process start. Host lifecycle must report healthy rendering or recover after failure; SDK does not force-restart the app.

## Later APK updates and recovery

A new APK ships its own hooks, native fallback bodies and matching bundled program. It starts that bundle and rejects older-base patches without clearing unrelated business data. Retain routes for older installations in the catalog. Actual Play Store delivery is not verified by local compatibility tests.

Lost-baseline recovery remains explicit: `patchrelay baseline --from YOUR_RELEASE_COMMIT --apk ORIGINAL_APK --name RECOVERED_NAME` rebuilds in a separate checkout and requires the original APK hash to match exactly. It cannot reconstruct missing keys or guess a release. Reproducibility depends on original toolchain/dependencies/environment.

Use [README verification commands](../README.md#verify) and the compiled cumulative workflow test before distributing. Current policy does not establish Google Play approval; see [distribution limits](production-readiness.md#distribution-and-security).
