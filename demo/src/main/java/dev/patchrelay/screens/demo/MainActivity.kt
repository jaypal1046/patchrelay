package dev.patchrelay.screens.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
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
    private var runner: IntPatchRunner? = null
    private var engine: ScreenEngine? = null
    private var recoveryAttempted = false
    private var renderGeneration = 0
    private var removeBackCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as DemoApp
        root = FrameLayout(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "Preparing checkout…"; textSize = 15f; setPadding(24, 24, 24, 16) }
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
            } catch (failure: Exception) { runOnUiThread { if (alive()) status.text = "Cannot prepare screen: ${failure.message}" } }
        }.start()
    }

    private fun show(program: ScreenProgram) {
        try { launchEngine(program) }
        catch (failure: Exception) { failed(program, failure) }
    }

    private fun launchEngine(program: ScreenProgram) {
        engine?.close()
        syncBack(false)
        runner = null
        calculate.isEnabled = false
        val generation = ++renderGeneration
        var confirmed = false
        val renderer = NativeScreenRenderer(this) { action, value -> engine?.event(action, value) }
        engine = ScreenEngine(this, "checkout", onTree = { tree ->
            if (alive() && generation == renderGeneration) {
                val view = renderer.build(tree)
                syncBack(tree.backAction != null)
                if (view.parent !== screen) { screen.removeAllViews(); screen.addView(view) }
                if (!confirmed) {
                    confirmed = true
                    engine!!.prepareIntFunctions(setOf("checkout.total.v1", "checkout.shipping.v1")) { prepared ->
                        runner = prepared
                        runNativeCalculation {
                            Thread {
                                try {
                                    app.updates.markHealthy()
                                    runOnUiThread { if (alive() && generation == renderGeneration) {
                                        status.text = "Native checkout · screen patch ${program.version}\n" +
                                            if (program.version == 0L) "Bundled Kotlin screen" else "Kotlin screen downloaded from GitHub"
                                        download.isEnabled = true
                                    } }
                                } catch (failure: Exception) { runOnUiThread { if (alive()) failed(program, failure) } }
                            }.start()
                        }
                    }
                }
            }
        }, onFailure = { failure -> if (alive() && generation == renderGeneration) failed(program, failure) })
        root.addView(engine!!.webView, 0, FrameLayout.LayoutParams(1, 1))
        engine!!.start(program.script)
    }

    private fun failed(program: ScreenProgram, failure: Exception) {
        engine?.close(); engine = null
        syncBack(false)
        runner = null; calculate.isEnabled = false
        ++renderGeneration
        if (program.version > 0 && !recoveryAttempted) {
            recoveryAttempted = true
            download.isEnabled = false
            status.text = "Patch failed; restoring bundled/previous screen…"
            Thread {
                try {
                    val fallback = app.updates.reject(app.bundled, failure)
                    app.selected = fallback
                    runOnUiThread { if (alive()) {
                        show(fallback)
                    } }
                } catch (restore: Exception) { runOnUiThread { if (alive()) status.text = "Recovery failed: ${restore.message}" } }
            }.start()
        } else {
            status.text = "Screen unavailable: ${failure.message}"
            download.isEnabled = true
        }
    }

    private fun stage() {
        download.isEnabled = false
        status.text = "Downloading signed screen program…"
        Thread {
            val message = try {
                app.updates.stage(BuildConfig.MANIFEST_URL)
                if (app.updates.pendingVersion() > 0) "Screen update verified. Force stop and reopen." else "No newer screen update. Active patch ${app.updates.activeVersion()}."
            } catch (failure: Exception) { "Update failed: ${failure.message}" }
            runOnUiThread { if (alive()) { status.text = message; download.isEnabled = true } }
        }.start()
    }

    private fun alive() = !isFinishing && !isDestroyed
    private fun originalTotal(quantity: Int, price: Int): Int = quantity + price
    private fun originalShipping(base: Int, extra: Int): Int = base + extra

    private fun runNativeCalculation(onReady: () -> Unit = {}) {
        val current = runner ?: return
        calculate.isEnabled = false
        current.call("checkout.total.v1", 3, 100, ::originalTotal) { total ->
            current.call("checkout.shipping.v1", 10, 5, ::originalShipping) { shipping ->
                calculation.text = "3 × 100 = $total · " +
                    (if (current.isPatched("checkout.total.v1")) "JS patch" else "original Kotlin") +
                    "\nUnpatched Kotlin shipping: $shipping"
                calculate.isEnabled = true
                onReady()
            }
        }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (engine?.back() != true) super.onBackPressed()
    }

    private fun syncBack(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 33 || (removeBackCallback != null) == enabled) return
        if (!enabled) { removeBackCallback?.invoke(); removeBackCallback = null; return }
        val callback = android.window.OnBackInvokedCallback { engine?.back() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        removeBackCallback = { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
    }

    override fun onDestroy() { ++renderGeneration; syncBack(false); engine?.close(); super.onDestroy() }
}
