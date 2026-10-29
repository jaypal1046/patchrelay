package com.patchrelayreactdemo

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import dev.patchrelay.reactnative.ReactNativeUpdates

class UpdatePackage(private val updates: ReactNativeUpdates) : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> = listOf(UpdateModule(context, updates))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

class UpdateModule(context: ReactApplicationContext, private val updates: ReactNativeUpdates) : ReactContextBaseJavaModule(context) {
    override fun getName() = "PatchRelayUpdates"
    override fun getConstants(): Map<String, Any> = mapOf("activeVersion" to updates.activeVersion().toDouble())

    @ReactMethod fun markHealthy(promise: Promise) {
        try { updates.markHealthy(); promise.resolve(true) }
        catch (failure: Exception) { promise.reject("HEALTH_FAILED", failure) }
    }

    @ReactMethod fun checkForUpdate(promise: Promise) {
        Thread {
            try {
                updates.stage("https://github.com/jaypal1046/patchrelay/releases/download/react-native-android-v1/patch.json")
                promise.resolve(updates.pendingVersion() > 0)
            } catch (failure: Exception) { promise.reject("UPDATE_FAILED", failure) }
        }.start()
    }

    @ReactMethod fun openAppInfo() {
        reactApplicationContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${reactApplicationContext.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
