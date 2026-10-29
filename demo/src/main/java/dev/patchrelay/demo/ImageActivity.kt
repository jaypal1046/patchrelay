package dev.patchrelay.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.patchrelay.Patch
import dev.patchrelay.PatchRelay
import java.io.File
import java.security.MessageDigest

class ImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply { text = "Image asset test"; textSize = 24f }
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, (220 * resources.displayMetrics.density).toInt())
        }
        val status = TextView(this).apply {
            text = "Loading image…"
            textSize = 16f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
        }
        val download = Button(this).apply { text = "Download image update"; isEnabled = false }
        val settings = Button(this).apply {
            text = "App info — force stop and reopen"
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(title)
            addView(preview)
            addView(status)
            addView(download)
            addView(settings)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        Thread({
            try {
                val session = loadSession(applicationContext)
                val current = "Image source: ${session.source}\nActive code patch: ${session.relay.activeVersion()}\nImage SHA-256: ${session.hash}" +
                    if (session.source == "APK baseline" && session.message.startsWith("Image not downloaded")) "\n${session.message}" else ""
                runOnUiThread {
                    preview.setImageBitmap(session.bitmap)
                    preview.contentDescription = if (session.source == "APK baseline") "Before image: orange circle" else "After image: blue check-mark tile"
                    status.text = current
                    if (session.relay.pendingVersion() > 0) status.append("\nUpdate staged. Force stop and reopen to apply.")
                    download.isEnabled = true
                    download.setOnClickListener {
                        download.isEnabled = false
                        status.text = "$current\nDownloading signed image-update code…"
                        Thread({
                            try {
                                session.relay.stage(BuildConfig.IMAGE_MANIFEST_URL)
                                runOnUiThread {
                                    status.text = current + if (session.relay.pendingVersion() > 0)
                                        "\nUpdate verified. Force stop and reopen. The new image downloads during startup."
                                    else "\nNo newer image update."
                                    download.isEnabled = true
                                }
                            } catch (error: Exception) {
                                runOnUiThread { status.text = "$current\nDownload failed: ${error.message}"; download.isEnabled = true }
                            }
                        }, "image-update-download").start()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { status.text = "Image test failed: ${error.javaClass.simpleName}\n${error.message}\nForce stop and reopen to recover." }
            } catch (error: LinkageError) {
                runOnUiThread { status.text = "Image test failed: ${error.javaClass.simpleName}\n${error.message}\nForce stop and reopen to recover." }
            }
        }, "image-update-start").start()
    }

    private data class Session(val relay: PatchRelay, val bitmap: Bitmap, val source: String, val hash: String, val message: String)

    private companion object {
        private var session: Session? = null

        @Synchronized
        fun loadSession(context: Context): Session {
            session?.let { return it }
            // Added UI preserves the existing greet/String ABI and Kotlin 1.9.23 baseline.
            val relay = PatchRelay(context, "3", BuildConfig.PATCH_PUBLIC_KEY)
            val logic = relay.start(Patch { _, input -> "Original logic: $input" })
            val message = logic.invoke("greet", "PatchRelay")
            val cached = File(context.cacheDir, "patchrelay-preview.png")
            val source: String
            val bytes: ByteArray
            if (cached.exists()) {
                check(cached.length() <= 512 * 1024) { "Cached image too large" }
                source = "downloaded cache image"
                bytes = cached.readBytes()
            } else {
                source = "APK baseline"
                bytes = context.assets.open("image-before.png").use { it.readBytes() }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            check(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) { "Invalid image dimensions" }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot decode image")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            relay.markHealthy()
            return Session(relay, bitmap, source, hash, message).also { session = it }
        }
    }
}
