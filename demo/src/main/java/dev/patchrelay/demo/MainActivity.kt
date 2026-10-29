package dev.patchrelay.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.patchrelay.Patch
import dev.patchrelay.PatchRelay

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "PatchRelay: loading…"
            textSize = 20f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val checkUpdate = Button(this).apply { text = "Check for update"; isEnabled = false }
        val appSettings = Button(this).apply {
            text = "Open app settings to restart"
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(status)
            addView(checkUpdate)
            addView(appSettings)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        Thread({
            try {
                val relay = PatchRelay(this, "3", BuildConfig.PATCH_PUBLIC_KEY)
                val logic = relay.start(Patch { _, input -> "Original Kotlin logic: hello $input" })
                val result = logic.invoke("greet", "PatchRelay")
                relay.markHealthy()
                val current = "$result\nActive patch: ${relay.activeVersion()}"
                val ready = "\nUpdate downloaded and verified.\nOpen app settings, tap Force stop, then reopen PatchRelay Demo."
                runOnUiThread {
                    status.text = current
                    if (relay.pendingVersion() > 0) {
                        status.append(ready)
                        appSettings.visibility = View.VISIBLE
                    }
                    checkUpdate.isEnabled = true
                    checkUpdate.setOnClickListener {
                        checkUpdate.isEnabled = false
                        status.text = "$current\nChecking GitHub for an update…"
                        Thread({
                            try {
                                relay.stage(BuildConfig.PATCH_MANIFEST_URL)
                                val pending = relay.pendingVersion() > 0
                                runOnUiThread {
                                    status.text = current + if (pending) ready else "\nYou're up to date."
                                    appSettings.visibility = if (pending) View.VISIBLE else View.GONE
                                    checkUpdate.isEnabled = true
                                }
                            } catch (error: Exception) {
                                Log.e("PatchRelayDemo", "Update failed", error)
                                runOnUiThread {
                                    status.text = "$current\nUpdate failed: ${error.message}\nTap Check for update to retry."
                                    checkUpdate.isEnabled = true
                                }
                            }
                        }, "patchrelay-download").start()
                    }
                }
            } catch (error: Exception) {
                Log.e("PatchRelayDemo", "Update failed", error)
                runOnUiThread { status.append("\n${error.message}") }
            }
        }, "patchrelay-demo").start()
    }
}
