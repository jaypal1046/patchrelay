package dev.patchrelay.screens.demo

import android.app.Application
import android.util.Log
import dev.patchrelay.screens.ScreenProgram
import dev.patchrelay.screens.ScreenUpdates
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class DemoApp : Application() {
    lateinit var updates: ScreenUpdates
    lateinit var prepared: CompletableFuture<ScreenProgram>
    lateinit var bundled: String
    @Volatile var selected: ScreenProgram? = null
    private val catalogChecked = AtomicBoolean(false)

    fun checkCatalogInBackground() {
        if (BuildConfig.CATALOG_URL.isEmpty() || !catalogChecked.compareAndSet(false, true)) return
        Thread {
            try { updates.stageCatalog(BuildConfig.CATALOG_URL, BuildConfig.UPDATE_CHANNEL) }
            catch (failure: Exception) { Log.w("PatchRelayDemo", "Background update check failed; current screen retained", failure) }
        }.start()
    }

    override fun onCreate() {
        super.onCreate()
        prepared = CompletableFuture.supplyAsync {
            bundled = assets.open("baseline-screen.js").bufferedReader().use { it.readText() }
            updates = ScreenUpdates(this, BuildConfig.BASE_VERSION, BuildConfig.PATCH_PUBLIC_KEY, BuildConfig.SCREEN_IDS.split(',').toSet(), BuildConfig.HOST_SHA256)
            updates.start(bundled).also { selected = it }
        }
    }
}
