package dev.patchrelay.screens.demo

import PatchRelayFunctions
import checkoutStatus
import kotlin.coroutines.*

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import dev.patchrelay.screens.*

class MainActivity : Activity() {
    private lateinit var app: DemoApp
    private lateinit var root: FrameLayout
    private lateinit var screen: FrameLayout
    private lateinit var status: TextView
    private lateinit var download: Button
    private lateinit var calculation: TextView
    private lateinit var calculate: Button
    private var runner: FunctionPatchRunner? = null
    private var screenReady = false
    private var engine: ScreenEngine? = null
    private val attemptedVersions = mutableSetOf<Long>()
    @Volatile private var renderGeneration = 0
    private var removeBackCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as DemoApp
        root = FrameLayout(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "Simple pages"; textSize = 15f; setPadding(24, 24, 24, 16) }
        screen = FrameLayout(this)
        calculation = TextView(this).apply { text = "Preparing native function…"; textSize = 17f; setPadding(24, 8, 24, 8) }
        calculate = Button(this).apply {
            text = "Run Kotlin function"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { runNativeCalculation() }
        }
        download = Button(this).apply {
            text = "Check screen update"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { stage() }
        }
        val settings = Button(this).apply {
            text = "App info — force stop and reopen"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
        column.addView(status)
        column.addView(calculation)
        column.addView(calculate)
        column.addView(screen, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(download)
        column.addView(settings)
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        Thread {
            try {
                val program = app.selected ?: app.prepared.get()
                runOnUiThread { if (alive()) show(program) }
            } catch (failure: Exception) {
                Log.e("PatchRelayDemo", "Program preparation failed", failure)
                try {
                    val fallback = ScreenProgram(0, assets.open("baseline-screen.js").bufferedReader().use { it.readText() })
                    app.bundled = fallback.script
                    app.selected = fallback
                    runOnUiThread { if (alive()) show(fallback) }
                } catch (fallback: Exception) { runOnUiThread { if (alive()) unavailable(fallback) } }
            }
        }.start()
    }

    private fun show(program: ScreenProgram) {
        if (!attemptedVersions.add(program.version)) {
            if (program.version > 0) show(ScreenProgram(0, app.bundled))
            else unavailable(IllegalStateException("Bundled screen already failed"))
            return
        }
        try { launchEngine(program) }
        catch (failure: Exception) { failed(program, failure) }
    }

    private fun launchEngine(program: ScreenProgram) {
        runner = null
        PatchRelayFunctions.installDispatcher(null)
        engine?.close()
        syncBack(false)
        screenReady = false
        calculate.isEnabled = false
        val generation = ++renderGeneration
        var confirmed = false
        var ready = false
        lateinit var latest: ScreenTree
        val renderer = NativeScreenRenderer(this) { action, value ->
            if (ready && generation == renderGeneration) engine?.event(action, value)
        }
        fun publish(tree: ScreenTree) {
            val view = renderer.build(tree)
            syncBack(tree.backAction != null)
            if (view.parent !== screen) { screen.removeAllViews(); screen.addView(view) }
            engine?.saveState { snapshot -> if (ready && generation == renderGeneration) app.saveState(snapshot) }
        }
        engine = ScreenEngine(this, "checkout", onTree = { tree ->
            if (alive() && generation == renderGeneration) {
                latest = tree
                renderer.build(tree)
                if (ready) publish(tree)
                if (!confirmed) {
                    confirmed = true
                    engine!!.prepareFunctions(PatchRelayFunctions.contracts) { prepared ->
                        runner = prepared
                        PatchRelayFunctions.installDispatcher(prepared::callSuspending)
                        runNativeCalculation { proof ->
                            Thread {
                                try {
                                    if (generation != renderGeneration) return@Thread
                                    if (program.version > 0 && !app.updates.markHealthy(program.version)) return@Thread
                                    runOnUiThread { if (alive() && generation == renderGeneration) {
                                        ready = true
                                        screenReady = true
                                        publish(latest)
                                        status.text = "Simple pages"
                                        calculation.text = proof
                                        calculate.isEnabled = true
                                        download.isEnabled = true
                                        app.checkCatalogInBackground()
                                    } }
                                } catch (failure: Exception) { runOnUiThread {
                                    if (alive() && generation == renderGeneration) failed(program, failure)
                                } }
                            }.start()
                        }
                    }
                }
            }
        }, onFailure = { failure -> if (alive() && generation == renderGeneration) failed(program, failure) })
        root.addView(engine!!.webView, 0, FrameLayout.LayoutParams(1, 1))
        engine!!.start(program.script, app.savedState)
    }

    private fun failed(program: ScreenProgram, failure: Exception) {
        Log.e("PatchRelayDemo", "Program ${program.version} rejected; selecting fallback", failure)
        runner = null; PatchRelayFunctions.installDispatcher(null)
        engine?.close(); engine = null
        syncBack(false)
        calculate.isEnabled = false
        screenReady = false
        screen.removeAllViews()
        calculation.text = ""
        status.text = "Simple pages"
        ++renderGeneration
        if (program.version > 0) {
            download.isEnabled = false
            Thread {
                try {
                    val fallback = app.updates.reject(app.bundled, failure)
                    app.selected = fallback
                    runOnUiThread { if (alive()) {
                        show(fallback)
                    } }
                } catch (restore: Exception) {
                    Log.e("PatchRelayDemo", "Cannot persist rollback; using bundled program in memory", restore)
                    val fallback = ScreenProgram(0, app.bundled)
                    app.selected = fallback
                    runOnUiThread { if (alive()) show(fallback) }
                }
            }.start()
        } else unavailable(failure)
    }

    private fun unavailable(failure: Exception) {
        Log.e("PatchRelayDemo", "Bundled checkout unavailable", failure)
        status.text = "Simple pages is temporarily unavailable."
        download.isEnabled = true
    }

    private fun stage() {
        download.isEnabled = false
        Thread {
            try {
                if (BuildConfig.CATALOG_URL.isNotEmpty()) app.updates.stageCatalog(BuildConfig.CATALOG_URL, BuildConfig.UPDATE_CHANNEL)
                else app.updates.stage(BuildConfig.MANIFEST_URL)
                Log.i("PatchRelayDemo", "Pending program: ${app.updates.pendingVersion()}")
            } catch (failure: Exception) { Log.w("PatchRelayDemo", "Update check failed; current screen retained", failure) }
            runOnUiThread { if (alive()) download.isEnabled = true }
        }.start()
    }

    private fun alive() = !isFinishing && !isDestroyed

    private fun runNativeCalculation(onReady: (String) -> Unit = { proof -> calculation.text = proof; calculate.isEnabled = true }) {
        val current = runner ?: return
        calculate.isEnabled = false
        PatchRelayFunctions.checkoutTotal(3, 100, current::call) { total ->
            PatchRelayFunctions.checkoutShipping(10, 5, current::call) { shipping ->
                val proof = "3 × 100 = $total · " +
                    "shared Kotlin" +
                    "\nShared Kotlin shipping: $shipping"
                val status: suspend () -> String = { checkoutStatus(false) }
                PatchRelayFunctions.rewardPoints(2, current::call) { points ->
                status.startCoroutine(Continuation(EmptyCoroutineContext) { result ->
                    if (!alive() || runner !== current) return@Continuation
                    result.fold({ onReady(proof + "\nShared Kotlin status: $it\nShared reward points: $points") }, { failure ->
                        failed(app.selected ?: return@fold, failure as? Exception ?: IllegalStateException(failure))
                    })
                })
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!screenReady || engine?.back() != true) super.onBackPressed()
    }

    private fun syncBack(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 33 || (removeBackCallback != null) == enabled) return
        if (!enabled) { removeBackCallback?.invoke(); removeBackCallback = null; return }
        val callback = android.window.OnBackInvokedCallback { engine?.back() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        removeBackCallback = { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
    }

    override fun onDestroy() { ++renderGeneration; syncBack(false); runner = null; PatchRelayFunctions.installDispatcher(null); engine?.close(); super.onDestroy() }
}
