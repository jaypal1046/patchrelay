# Native screen runtime — API 2

## Build contract

`runtime.json` owns application ID, base version, registered screen IDs, channel and native fingerprint inputs. Gradle and the Python builder compute identical sorted path/content hashes, normalizing text CRLF to LF. The host fingerprint includes the bundled program, renderer, manifest, build settings and public key.

A format-3 baseline records native and screen source hashes, host fingerprint and APK/program hashes. A patch build rejects added, changed or removed native inputs. Changed supported Kotlin screen sources compile as one Kotlin/JS program; this is neither a binary delta nor automatic method extraction.

Schema-3 signed metadata binds runtime `kotlin-screen-js`, API 2, app/base identity, host fingerprint, registered screen IDs, positive version, artifact URL and SHA-256. The runtime authenticates exact payload bytes before using metadata. API-1 manifests do not activate in API 2. The SDK's API version is fixed by its implementation; configuration cannot claim unsupported capabilities.

## Runtime boundaries

One application-owned `ScreenUpdates` selects a program on a worker thread. HTTPS downloads are bounded; scripts are limited to 2 MiB. Atomic script/metadata writes precede accepted-version and pending-state commits. Versions cannot replay over the accepted high-water mark. Persisted scripts are verified again at startup.

The interpreter is a hidden, local Android WebView without a JavaScript interface or network/file/content access. It calls:

- `renderScreen(screenId): String`
- `handleEvent(screenId, action, value): String`

Both return a JSON screen tree. Events carry an action plus optional string value. A bounded FIFO coalesces adjacent field changes without crossing clicks. Intermediate queued results are validated but not displayed, preventing old results from resetting newer typing.

The runner proof also exports `patchedFunctionIds(): String` (JSON ID array) and `invokePatch(id, first, second): String` (integer result). `IntPatchRunner` validates the declared IDs against prepared native hooks. Calls without bindings use the installed Kotlin body; bound calls use JS. Native code is not overwritten. Supported arguments/results are two 32-bit integers and one 32-bit integer, delivered through a callback. Hooks are explicit, not compiler-generated.

`ScreenInterpreter` owns one WebView and a bounded serial call queue shared by screen rendering and function calls. It closes with the Activity. A registered patch error takes the program failure path; it does not automatically invoke the native body after possible side effects.

## UI contract

The root identifies a registered screen and contains a column. Every child has a unique stable ID. Supported nodes:

| Node | Fields beyond id/type |
| --- | --- |
| column, row, card | children |
| text | text, style: title/body/caption/error |
| button | text, action, enabled |
| input | text (accessible label/hint), value, action, enabled |
| toggle | text, checked, action, enabled |

Unknown types or fields, duplicate IDs, incorrect primitive types and malformed actions fail validation. Limits: 64 KiB tree, 128 nodes, depth 8, 32 children/layout, 1,024 characters/text or input. Rows use equal-width children. Layout spacing and card appearance belong to the installed renderer.

`NativeScreenRenderer` retains views by ID and type, reconciles child ordering and updates properties. Unchanged input text is not assigned again; program-driven replacements clamp selection. Model application suppresses input callbacks. Removing a field or changing its type can lose focus. No state survives process restart.

## Activation and recovery

Stage now; activate next process start. Storage identity includes base version and host fingerprint. An unconfirmed patched startup rolls back. Health is recorded after the initial validated native tree attaches, not after every future interaction. Runtime event failures may also trigger fallback; no transaction/data rollback is provided. Timeouts prepare fallback and require restart.

The Activity owns and closes its interpreter and timeout callbacks. Native view construction failures take the same recovery path. Resource exhaustion or WebView/provider failures remain operating risks.

## Native boundary

The installed renderer cannot fix itself through a screen patch. Unsupported native widgets, dependencies, permissions and unintegrated native screens need a new base APK. Existing Android Compose/Activity source does not automatically compile to this API.
