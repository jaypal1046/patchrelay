# Kotlin-only functional patches: research decision

Status: proposed experiment, not an implemented feature or a Play approval claim. Target is a native Android Kotlin app. React Native and Flutter remain outside scope.

## What narrowing the scope changes

Keeping dependencies, resources and native APIs unchanged reduces compatibility work. It does not make an externally downloaded DEX patch compliant with Google Play. A method-body-only DEX patch is still executable DEX.

The most practical candidate to test is Kotlin-authored logic compiled to an interpreted payload, with a dispatch boundary and interpreter included in the original store-distributed APK. This changes which implementation a prepared call invokes; it does not overwrite the installed native method.

## First experiment

Use one pure Kotlin function whose bug affects a native screen:

```kotlin
// Before: a missing server-provided name causes an exception.
fun displayName(name: String?): String = name!!.trim()

// Fix, authored in Kotlin:
fun displayName(name: String?): String =
    name?.trim()?.takeIf { it.isNotEmpty() } ?: "Guest"
```

Keep this logic in a small platform-independent Kotlin module. Kotlin Multiplatform can compile common source for JVM and JavaScript. The first APK contains the native UI, its original implementation, a dispatcher and an interpreter. The UI invokes the dispatcher rather than directly calling an unchangeable native implementation.

After fixing the Kotlin source, the publisher compiles its compatible logic to JavaScript, tests it, signs the exact payload bytes and hosts it over HTTPS. The app verifies the signature, exact base and function contract before selecting the interpreted implementation on a later launch. The screen displays the new function result through its existing native widgets.

No developer-written JavaScript is required by this proposed workflow. A JavaScript payload is still required at runtime. Kotlin/JS compiles compatible Kotlin and libraries; it does not translate arbitrary Android/JVM code. This experiment requires initial integration and isolation of eligible functions.

An existing `Patch.invoke(operation, input)` boundary could be reused in a prototype, but the current DEX staging path and schema must not silently accept script payloads. A separately identified artifact contract would be needed. No production manifest or SDK changes are made by this research note.

## Interpreter and integration constraints

Android WebView is a possible first experimental engine because it already supports JavaScript evaluation. It need not render the app's UI. Its evaluation API is asynchronous and runs through the UI thread, so the native call boundary must support that execution model; it cannot pretend to be a transparent synchronous Kotlin method replacement. Keep the execution context local, deny arbitrary navigation/network/file access, and avoid exposing unrestricted reflection or Android APIs.

A different embedded engine could be evaluated if WebView cannot meet lifecycle, timeout or performance requirements. Adding a new engine to the initial base is distinct from downloading native engine changes later. Engine selection remains unverified.

Start with nullable strings, booleans, numbers, branches and explicitly supported collection operations. Reject unsupported functions at build time. Do not silently accept Activity/Fragment/Compose methods, Android/JVM-specific dependencies, arbitrary object fields, reflection, suspend functions or native side effects in the initial experiment. Future support would require explicit semantics and tests.

## Pass criteria before claiming success

1. Native release APK uses no React Native or Flutter runtime. Demonstrate the original exception through a controlled test action and also exercise a valid input.
2. Change only the Kotlin function body; retain its input/output contract and all native dependencies, resources and assets.
3. Produce a signed interpreted payload, not downloaded DEX, JAR, APK or native libraries.
4. Download from GitHub on a physical phone. Re-run the same action and observe `Guest` for null/blank input; valid-input behavior must remain correct.
5. Record unchanged installed APK SHA-256, version and installation timestamp across the update.
6. Verify tampering/incompatible-contract rejection, unavailable-engine behavior, failed-start recovery and a second successful restart. Bound execution and test failure behavior rather than assuming interpreter errors are harmless.
7. Document threading, startup cost and supported Kotlin constructs. Passing these tests establishes the tested behavior only; it does not establish store acceptance.

Only after this works should build-time tooling automate routing eligible functions. A custom Kotlin compiler plugin is one possible research route, but Kotlin documents its APIs as unstable and maintenance-intensive. It is not necessary for the first manually integrated function experiment, and no automatic extraction of arbitrary Android method bodies is promised.

## Other routes checked

| Route | Finding |
| --- | --- |
| Smaller DEX or binary delta | Same external executable-code restriction |
| ART/JVMTI method replacement | Normal attach-agent support requires a debuggable app; not a production hot-patch solution |
| Native `.so` hooks | Downloaded native code is also explicitly restricted |
| Configuration/server responses | Useful for prepared behavior, but cannot repair arbitrary native code |
| Existing native screen without a prepared dispatch boundary | No verified Play-compatible in-place code replacement found |

## Sources and policy boundary

- [Google Play Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en): external DEX/JAR/native code restriction and the interpreter/VM exception with indirect Android API access. All other policies still apply. The proposed design may fit that exception; no Google approval has been obtained.
- [Kotlin/JS](https://kotlinlang.org/docs/js-overview.html): compatible Kotlin code and dependencies can compile to JavaScript.
- [Kotlin Multiplatform source-set constraints](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html): common code cannot directly use platform-specific APIs.
- [Android WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,android.webkit.ValueCallback)): asynchronous evaluation and UI-thread requirements.
- [Custom Kotlin compiler plugins](https://kotlinlang.org/docs/custom-compiler-plugins.html): plugins can transform declarations/bodies, but APIs are unstable and require ongoing maintenance.
- [ART TI](https://source.android.com/docs/core/runtime/art-ti): attach-agent restrictions for debuggable apps.
