package dev.patchrelay.visual

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.patchrelay.Patch
import dev.patchrelay.PatchRelay
import java.io.File

/** Two separate installations per case; neither APK contains patched implementation classes. */
class MainActivity : Activity() {
    private lateinit var relay: PatchRelay
    private lateinit var status: TextView
    private lateinit var download: Button
    private lateinit var close: Button
    private var current = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val role = if (BuildConfig.CAN_UPDATE) "PATCH APP — starts with original code" else "BEFORE APP — original code"
        val header = TextView(this).apply {
            text = "${BuildConfig.TEST_TITLE}\n$role"
            textSize = 22f
        }
        status = TextView(this).apply {
            text = "Loading actual result…"
            textSize = 18f
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        download = Button(this).apply {
            id = android.R.id.button1
            text = "1. Download signed GitHub patch"
            visibility = if (BuildConfig.CAN_UPDATE) View.VISIBLE else View.GONE
            isEnabled = false
            setOnClickListener { downloadPatch() }
        }
        close = Button(this).apply {
            id = android.R.id.button2
            text = "2. Close app, then reopen its icon"
            visibility = View.GONE
            setOnClickListener { finishAndRemoveTask(); Process.killProcess(Process.myPid()) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(header)
            addView(status)
            addView(download)
            addView(close)
            addView(TextView(this@MainActivity).apply {
                text = "\nCompare with the same-numbered ${if (BuildConfig.CAN_UPDATE) "Before" else "Patch"} app.\nPackage: $packageName"
            })
        }
        setContentView(ScrollView(this).apply { addView(content) })
        Thread({
            try {
                relay = PatchRelay(applicationContext, "visual-1", BuildConfig.PATCH_PUBLIC_KEY)
                val logic = relay.start(Patch { _, _ -> "Original logic: hello phone" })
                val active = relay.activeVersion() > 0
                val file = File(filesDir, "visible-data.json")
                if (!file.exists()) file.writeText("{\"revision\":1,\"title\":\"Original private file\"}")
                var failed = false
                val result = try { result(logic, active, file) }
                catch (error: Exception) { failed = true; "Operation failed: ${error.javaClass.simpleName}\n${error.message}" }
                catch (error: LinkageError) { failed = true; "Operation failed: ${error.javaClass.simpleName}\n${error.message}" }
                val exerciseRollback = active && BuildConfig.TEST_CASE == "filedata"
                if (!failed && !exerciseRollback) relay.markHealthy()
                current = "Active patch: ${relay.activeVersion()}\n\n$result"
                if (failed) current += "\n\nFailure is visible; patch not marked healthy. Reopening after closing rolls code back."
                if (exerciseRollback) current += "\n\nFile changed. Close/reopen again to test rollback: code returns to patch 0, but the file stays changed."
                runOnUiThread {
                    status.text = current
                    download.isEnabled = true
                    if (relay.pendingVersion() > 0 || exerciseRollback || failed) close.visibility = View.VISIBLE
                    if (relay.pendingVersion() > 0) status.append("\n\nPatch downloaded. Close and reopen to apply.")
                }
            } catch (error: Exception) {
                runOnUiThread { status.text = "Startup failed: ${error.javaClass.simpleName}\n${error.message}" }
            }
        }, "visual-test-start").start()
    }

    private fun result(logic: Patch, active: Boolean, file: File): String = when (BuildConfig.TEST_CASE) {
        "r8host", "r8patch" -> logic.invoke("greet", "phone")
        "assets" -> {
            val original = assets.open("catalog.json").bufferedReader().use { it.readText() }
            "APK asset:\n$original" + if (active) "\nPatch resource: ${logic.invoke("readPatchAsset", "")}\nAPK asset remains unchanged." else ""
        }
        "filedata" -> {
            if (active) logic.invoke("writeFile", file.absolutePath)
            "Actual private file:\n${file.readText()}"
        }
        "missingdep", "newdep" -> if (active) {
            "Input: hello\nCodec SHA-256:\n${logic.invoke("newDependency", "hello")}"
        } else {
            val present = try { Class.forName("org.apache.commons.codec.digest.DigestUtils"); true }
            catch (_: ClassNotFoundException) { false }
            "Codec available in installed app: $present\nNo dependency patch applied."
        }
        "upgrade" -> {
            val version = Class.forName("com.google.gson.internal.GsonBuildConfig").getField("VERSION").get(null)
            if (active) {
                try { "Host Gson: $version\nNew API result: ${logic.invoke("newDependencyApi", "hello")}" }
                catch (error: NoSuchMethodError) { throw NoSuchMethodError("Host Gson remains $version; patch requests Gson 2.10.1 API. ${error.message}") }
            } else "Gson version in installed app: $version\nNo dependency upgrade applied."
        }
        else -> error("Unknown test case")
    }

    private fun downloadPatch() {
        download.isEnabled = false
        status.text = "$current\n\nDownloading from GitHub…"
        Thread({
            try {
                relay.stage(BuildConfig.MANIFEST_URL)
                val pending = relay.pendingVersion() > 0
                runOnUiThread {
                    status.text = "$current\n\n" + if (pending) "Downloaded and signature verified. Close and reopen to apply." else "No newer patch."
                    close.visibility = if (pending) View.VISIBLE else View.GONE
                    download.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "$current\n\nDownload failed: ${error.message}"
                    download.isEnabled = true
                }
            }
        }, "visual-test-download").start()
    }
}
