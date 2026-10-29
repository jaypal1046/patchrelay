package dev.patchrelay.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import dev.patchrelay.Patch
import dev.patchrelay.PatchRelay

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "PatchRelay: loading…"
            textSize = 20f
            setPadding(32, 48, 32, 32)
        }
        setContentView(status)
        Thread({
            try {
                val relay = PatchRelay(this, "2", BuildConfig.PATCH_PUBLIC_KEY)
                val logic = relay.start(Patch { _, input -> "Original Kotlin logic: hello $input" })
                val result = logic.invoke("greet", "PatchRelay")
                relay.markHealthy()
                runOnUiThread { status.text = "$result\nActive patch: ${relay.activeVersion()}" }
                intent.getStringExtra("manifestUrl")?.let { manifestUrl ->
                    val staged = relay.stage(manifestUrl)
                    runOnUiThread {
                        status.append(if (staged) "\nUpdate verified. Close and reopen app." else "\nAlready current.")
                    }
                }
            } catch (error: Exception) {
                Log.e("PatchRelayDemo", "Update failed", error)
                runOnUiThread { status.append("\n${error.message}") }
            }
        }, "patchrelay-demo").start()
    }
}
