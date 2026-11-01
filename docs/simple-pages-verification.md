# Simple pages: CLI-only baseline and patch verification

The old `screens/src` and previous settings/tool scripts were preserved under the ignored private folder `.patchrelay/backups/simple-pages-20261101/`. The new app uses separate Kotlin files for App, Navigation, Home, Catalog, Cart, Profile, Functions and State. No handwritten JavaScript bridge or duplicate implementation is maintained.

The initial v1 app has four pages, a $12 notebook and 10 reward points per item. The v2 patch changes all four pages/navigation, lowers the price to $9, doubles rewards, adds Offers and removes the obsolete BaselineInfo helper. Saved name and quantity use explicit scalar snapshots. An unknown saved destination returns Home, allowing the older bundled UI to recover safely from a destination introduced by a patch.

## Build environment and settings

Commands below run from `C:\Jay\dev\AutoUpdate`. Use the local CLI, not an older published alpha package. JDK 17 is installed locally. An IDE-created Kotlin daemon on Java 25 was incompatible with Kotlin 1.9.23, so this project's CLI compile/base task lists include `-Pkotlin.compiler.execution.strategy=in-process`.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
function patchrelay { node C:/Jay/dev/AutoUpdate/cli/bin/patchrelay.mjs @args }
```

`patchrelay init` was run interactively to create the new settings. It preserves SDK location settings and keeps private key contents out of configuration. The existing 3072-bit private key matches the public key pinned in the APK; no private key is transferred to the phone. The `updates.invalid` URLs are explicit placeholders, not live hosting.

## Initial native build and real phone

These commands record the v1 phase. The final working tree is the v2 patch; rebuilding an APK from v2 deliberately creates a different bundled base. The immutable v1 source archive remains in the named baseline.

```powershell
.\gradlew.bat :demo:assembleRelease :demo:assembleReleaseAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=1
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$phoneSerial = 'VKIVIFSCPJZ5OBTK' # RMX2117, real phone
& $adb -s $phoneSerial install -r demo/build/outputs/apk/release/demo-release.apk
& $adb -s $phoneSerial install -r demo/build/outputs/apk/androidTest/release/demo-release-androidTest.apk
& $adb -s $phoneSerial shell am instrument -w -e action generated_workflow -e edition baseline -e version 0 dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
& $adb -s $phoneSerial shell am force-stop dev.patchrelay.screens.demo
& $adb -s $phoneSerial shell am instrument -w -e action generated_restore -e edition baseline -e version 0 dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
patchrelay baseline --name simple-pages-v1
```

Normal release builds also record an immutable artifact-hash baseline automatically. The CLI command creates a developer-named record of the same release. App data is preserved by `install -r`; state selection is isolated by the new base/fingerprint.

## Compare and sign the multi-file patch

After editing supported Kotlin and updating its tests:

```powershell
$baseline = '.patchrelay/releases/baselines/simple-pages-v1/baseline.json'
$patch = '.patchrelay/releases/patches/simple-pages-v1/1'
patchrelay compare --baseline $baseline --diff
patchrelay patch --baseline $baseline --version 1 --url https://updates.invalid/simple-pages/v1/screens.js --out $patch
patchrelay catalog --patch "$patch/patch.json" --catalog new --channel production --url https://updates.invalid/simple-pages/v1/patch.json --out .patchrelay/releases/catalogs/simple-pages-v1
node tools/test-kotlin-workflow.mjs $baseline $patch
```

Expected comparison: 7 modified application files, 1 added OffersPage file and 1 removed BaselineInfo file, with no native-input changes. State and installed function signatures stay compatible. Shared Kotlin tests run before signing. The patch is a complete cumulative JS program. Version 1 is reserved for this exact base after successful signing; reruns must choose a higher version and a new output directory.

## Real-phone patch transport and checks

The test companion imports CLI artifacts over USB into a test-only bounded byte transport. It invokes normal `ScreenUpdates.stageCatalog` with the APK's actual pinned key, application/channel/base/fingerprint. Catalog/manifest signatures, artifact hash and replay checks remain active. It does not write activation preferences manually or replace the bundled APK program. This verifies local activation, not HTTPS delivery; nothing is uploaded or published.

```powershell
$remote = '/sdcard/Android/data/dev.patchrelay.screens.demo/files'
& $adb -s $phoneSerial shell mkdir -p $remote
& $adb -s $phoneSerial push "$patch/screens.js" "$remote/screens.js"
& $adb -s $phoneSerial push "$patch/patch.json" "$remote/patch.json"
& $adb -s $phoneSerial push .patchrelay/releases/catalogs/simple-pages-v1/catalog.json "$remote/catalog.json"
& $adb -s $phoneSerial shell am instrument -w -e action stage_real_patch dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
& $adb -s $phoneSerial shell am force-stop dev.patchrelay.screens.demo
& $adb -s $phoneSerial shell am instrument -w -e action generated_workflow -e edition patch -e version 1 dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
& $adb -s $phoneSerial shell am force-stop dev.patchrelay.screens.demo
& $adb -s $phoneSerial shell am instrument -w -e action generated_restore -e edition patch -e version 1 dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
& $adb -s $phoneSerial shell am instrument -w -e action reject_replay -e edition patch -e version 1 dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
```

Before staging the valid patch, `reject_bad_patch` checks separately altered JS bytes and altered signed metadata. Both must fail with no pending version. The valid files are then restored and accepted at the same version, proving rejection did not consume it. The phone checks use actual native widgets: page navigation, item additions/removals, profile editing, generated native reward dispatch and fresh-process persistence. At two items, v1 displays total $24 / rewards 20; v2 displays total $18 / rewards 40. The native reward hook also changes from 20 to 40 while the installed APK stays unchanged.

Execution logs and UI/installed-artifact evidence are kept privately under `.patchrelay/simple-pages-verification/`. Remaining limitations are the documented bounded Kotlin/Compose backend and untested hosted HTTPS delivery; this is not arbitrary Android/Material conversion or a Play approval claim.

## Recorded result — 2026-11-01

- CLI `init`, native release build, named `baseline`, 9-file `compare --diff`, signed `patch --version 1` and signed `catalog` completed.
- Real phone: RMX2117, serial `VKIVIFSCPJZ5OBTK`; no emulator used for this sequence.
- `generated_workflow` and `generated_restore` passed for v1/version 0 and v2/version 1; v2 checks cover all five pages and the generated native reward hook.
- Altered JS, invalid manifest signature and invalid catalog signature were rejected separately; valid artifacts then staged successfully at version 1. Replay was rejected after activation.
- The first v2 check expected the prepared Cart route but the phone was on Home with the quantity preserved. CLI UI inspection confirmed v2/native reward 40; after selecting Basket, full workflow and restart checks passed. These checks require the prepared quantity/name state (2/Jay); interactive navigation can change the expected starting route.
- APK pulled from the real phone before and after patch activation has identical SHA-256: `17375f5feb3585f82fdfa6425e2d56569d122805111e841b840d660329c0b166`. It exactly matches the named baseline APK. No APK was installed after creating the baseline.
- Native fingerprint: `3ee49163567303bd8124b01b6367de4b0d3b222246906df6949039db0acd692c`. Current working sources are v2; the APK and bundled fallback remain v1.
- Shared JVM tests, SDK unit tests, demo debug/release recovery tests, restricted-worker browser tests and signature/base/state program checks passed. Nothing was pushed or published. USB transport proves local signed activation, not hosted HTTPS downloading.
