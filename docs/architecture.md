# Native screen engine

Current scope: a native Android host renders supported Kotlin-authored replacement screens. No DEX loading, React Native or Flutter runtime remains in the active project.

## Build side

1. The developer supplies the local project and an exact release baseline.
2. Native/asset/compiler source hashes are compared with the baseline; unsupported changes fail the build. Text line endings are normalized across Windows/Linux.
3. Changed files under the supported common Kotlin screen module are identified. The whole module is compiled with Kotlin/JS, including its used shared logic.
4. Tests validate the exported functions, tree contract and intended behavior. A schema-3 manifest signs app/base identity, engine API, stable screen IDs, version, artifact URL and SHA-256.

This first tool is explicitly configured for this repository. It is not a compiler plugin that automatically discovers or rewrites arbitrary existing Android screens.

## Device side

The application owns one `ScreenUpdates` instance and prepares a selected `ScreenProgram` on a worker thread. Every Activity, including restored Activities, uses that same selection. It falls back to the packaged program when there is no usable patch.

`ScreenEngine` executes the program in a hidden local WebView. It calls the compiler's exported `renderScreen` and `handleEvent` functions asynchronously. There is no JavaScript interface exposing Android objects. Network/file/content access and external navigation are disabled.

The interpreter returns a JSON column containing text and button nodes. `ScreenTrees` rejects wrong screen IDs, unknown widget types/fields, malformed actions, excessive widgets or oversized output. `NativeScreenRenderer` builds a detached native view tree; the host swaps it in only after successful validation/construction. Native clicks invoke program event handling, which returns the next tree.

The baseline screen's Kotlin logic is replaced through the program boundary; installed native methods and renderer implementations are not rewritten. The base contains both renderer and interpreter integration from the first release.

## Versioning and trust

Downloaded scripts are limited to 2 MiB. HTTPS transport is bounded. RSA signatures authenticate exact payload bytes before compatibility metadata is trusted; SHA-256 authenticates the script. Schema 3, runtime `kotlin-screen-js`, screen API 1 and the registered screen set are required. Earlier manifest formats are rejected.

Stage now, activate on the next process start. Atomic script/metadata writes occur before the accepted version and pending state are committed. Interrupted unaccepted files can be replaced on retry. Active files cannot be replaced by replaying an accepted version. The script is authenticated again before startup execution.

Startup remains unconfirmed until a valid native tree renders. Failures prepare the previous or bundled program; a renderer timeout requires restarting the process. The Activity owns and disposes the WebView and timers. A process killed before confirmation conservatively rolls back next time.

A signed program has trusted authority over its supported UI; signing keys must be protected. Limits and disabled WebView features reduce exposure but are not a complete isolation guarantee. The prototype has no key rotation/revocation, persistent program state or general transaction rollback.

## Unchanged hard boundary

A bug in the installed engine, unsupported native widget/capability or an unintegrated native screen still needs a new native base. Kotlin source must use this supported screen API; existing Android Compose/Activity code does not automatically become patchable. Platform/store eligibility remains unverified.
