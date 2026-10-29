# PatchRelay React Native Android example

Pinned React Native 0.82.1 / Hermes release app. Uses the shared PatchRelay core and the Android JS-bundle adapter. Native bridge integration lives in `android/app/src/main/java/com/patchrelayreactdemo/UpdatePackage.kt`.

See [build, integration, compatibility and test instructions](../../docs/react-native-android.md).

`App.tsx` is the orange APK baseline. `index.patch.js` registers the blue replacement screen. Both use `UpdateScreen.tsx`, which confirms JS startup and exposes the signed-update flow. No Metro server is used by the release app.

The generated iOS scaffold has not been integrated or tested and is excluded from this Android example's tracked sources. This example is not a production signing configuration or a published npm SDK.
