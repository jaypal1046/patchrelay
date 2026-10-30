package dev.patchrelay.screens.demo

import android.app.Application
import dev.patchrelay.screens.ScreenProgram
import dev.patchrelay.screens.ScreenUpdates
import java.util.concurrent.CompletableFuture

class DemoApp : Application() {
    lateinit var updates: ScreenUpdates
    lateinit var prepared: CompletableFuture<ScreenProgram>
    lateinit var bundled: String
    @Volatile var selected: ScreenProgram? = null

    override fun onCreate() {
        super.onCreate()
        updates = ScreenUpdates(this, BuildConfig.BASE_VERSION, BuildConfig.PATCH_PUBLIC_KEY, setOf("checkout"))
        prepared = CompletableFuture.supplyAsync {
            bundled = assets.open("baseline-screen.js").bufferedReader().use { it.readText() }
            updates.start(bundled).also { selected = it }
        }
    }
}
