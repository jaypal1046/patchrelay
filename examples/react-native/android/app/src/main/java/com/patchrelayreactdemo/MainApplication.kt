package com.patchrelayreactdemo

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import dev.patchrelay.reactnative.ReactNativeUpdates
import java.util.concurrent.CompletableFuture

class MainApplication : Application(), ReactApplication {
  lateinit var updates: ReactNativeUpdates
  var bundlePath: String? = null
    private set
  private lateinit var preparedBundle: CompletableFuture<String?>

  @Synchronized fun prepareBundle() {
    bundlePath = preparedBundle.get()
  }

  override val reactHost: ReactHost by lazy {
    // Restored React activities can bypass the launcher; reuse the same verified selection.
    prepareBundle()
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(UpdatePackage(updates))
        },
      jsBundleFilePath = bundlePath,
      useDevSupport = false,
    )
  }

  override fun onCreate() {
    super.onCreate()
    updates = ReactNativeUpdates(this, "rn-0.82.1-hermes-base1", BuildConfig.PATCH_PUBLIC_KEY)
    preparedBundle = CompletableFuture.supplyAsync { updates.start() }
    loadReactNative(this)
  }
}
