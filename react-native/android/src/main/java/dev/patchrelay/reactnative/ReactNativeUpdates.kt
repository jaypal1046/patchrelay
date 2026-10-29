package dev.patchrelay.reactnative

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import dev.patchrelay.core.HttpsDownloads
import dev.patchrelay.core.SignedManifests
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** One instance in one app process; ReactHost consumes the selected bundle path. */
class ReactNativeUpdates @Throws(IOException::class) constructor(context: Context, private val baseVersion: String, private val publicKey: String) {
    private val app = context.applicationContext
    private val directory: File
    private val state: SharedPreferences
    private var started = false
    private var sessionVersion = 0L

    init {
        require(baseVersion.isNotBlank()) { "Base compatibility identifier required" }
        directory = File(app.noBackupFilesDir, "patchrelay-react-native/" + UUID.nameUUIDFromBytes(baseVersion.toByteArray(Charsets.UTF_8)))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create bundle directory")
        state = app.getSharedPreferences("patchrelay-react-native", Context.MODE_PRIVATE)
        if (state.getString("base", "") != baseVersion) save(state.edit().clear().putString("base", baseVersion))
    }

    /** Worker thread, once before creating ReactHost. Null selects the APK's bundled JS. */
    @Synchronized
    @Throws(Exception::class)
    fun start(): String? {
        workerThread()
        check(!started) { "Call start once before creating ReactHost" }
        started = true
        var active = state.getLong("active", 0)
        if (state.getBoolean("booting", false)) {
            active = state.getLong("previous", 0)
            save(state.edit().putLong("active", active).putLong("previous", 0).putBoolean("booting", false))
        }
        val pending = state.getLong("pending", 0)
        if (pending > 0) {
            save(state.edit().putLong("previous", active).putLong("active", pending).remove("pending"))
            active = pending
        }
        if (active == 0L) return null
        save(state.edit().putBoolean("booting", true))
        try {
            return checkedBundle(active).also { sessionVersion = active }
        } catch (failure: Exception) {
            Log.e("PatchRelay", "Rejected React Native bundle", failure)
            val previous = state.getLong("previous", 0)
            save(state.edit().putLong("active", previous).putLong("previous", 0).putBoolean("booting", previous > 0))
            if (previous > 0 && previous != active) {
                try { return checkedBundle(previous).also { sessionVersion = previous } }
                catch (fallback: Exception) { Log.e("PatchRelay", "Rejected fallback bundle", fallback) }
            }
            save(state.edit().putLong("active", 0).putBoolean("booting", false))
            return null
        }
    }

    /** Downloads only. Activation requires a fresh process and a newly created ReactHost. */
    @Synchronized
    @Throws(Exception::class)
    fun stage(manifestUrl: String): Boolean {
        workerThread()
        val envelope = HttpsDownloads.download(manifestUrl, 64 * 1024)
        val manifest = verify(envelope)
        val version = manifest.getLong("version")
        if (version <= state.getLong("highest", 0)) return false
        val bytes = HttpsDownloads.download(manifest.getString("artifactUrl"), MAX_BUNDLE)
        checkHash(bytes, manifest.getString("sha256"))
        if (bytes.isEmpty()) throw SecurityException("Empty JavaScript bundle")
        val bundle = File(directory, "$version.bundle")
        val metadata = File(directory, "$version.json")
        if (bundle.exists() || metadata.exists()) throw IOException("Bundle version already exists")
        try {
            write(bundle, bytes)
            write(metadata, envelope)
            save(state.edit().putLong("highest", version).putLong("pending", version))
        } catch (failure: Exception) {
            bundle.delete()
            metadata.delete()
            throw failure
        }
        return true
    }

    /** Call from JS after the updated root component successfully mounts. */
    @Synchronized
    @Throws(IOException::class)
    fun markHealthy() {
        check(started) { "Call start first" }
        if (sessionVersion > 0) save(state.edit().putBoolean("booting", false))
    }

    @Synchronized fun activeVersion(): Long = sessionVersion
    @Synchronized fun pendingVersion(): Long = state.getLong("pending", 0)

    private fun checkedBundle(version: Long): String {
        val metadata = File(directory, "$version.json")
        if (metadata.length() > 64 * 1024) throw SecurityException("Manifest too large")
        val manifest = verify(metadata.readBytes())
        if (manifest.getLong("version") != version) throw SecurityException("Bundle version mismatch")
        val bundle = File(directory, "$version.bundle")
        if (bundle.length() !in 1..MAX_BUNDLE.toLong()) throw SecurityException("Invalid bundle size")
        checkHash(bundle.readBytes(), manifest.getString("sha256"))
        return bundle.absolutePath
    }

    private fun verify(bytes: ByteArray): JSONObject =
        SignedManifests.verify(bytes, publicKey, app.packageName, baseVersion, "react-native-android")

    private fun checkHash(bytes: ByteArray, expected: String) {
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != expected) throw SecurityException("Bundle hash mismatch")
    }

    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (failure: IOException) { atomic.failWrite(stream); throw failure }
    }

    private fun save(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Cannot persist bundle state")
    }

    private fun workerThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Use a worker thread" }
    }

    private companion object { const val MAX_BUNDLE = 16 * 1024 * 1024 }
}
