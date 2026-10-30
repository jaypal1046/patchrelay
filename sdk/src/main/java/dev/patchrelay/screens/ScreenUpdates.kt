package dev.patchrelay.screens

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import dev.patchrelay.core.HttpsDownloads
import java.io.File
import java.io.IOException
import java.util.UUID

data class ScreenProgram(val version: Long, val script: String)

/** One instance per designated process. Programs contain interpreted JS, never DEX. */
class ScreenUpdates(context: Context, private val baseVersion: String, private val publicKey: String,
    private val screenIds: Set<String>) {
    private val app = context.applicationContext
    private val directory: File
    private val state: SharedPreferences
    private var started = false
    private var sessionVersion = 0L

    init {
        require(baseVersion.isNotBlank() && screenIds.isNotEmpty())
        directory = File(app.noBackupFilesDir, "screen-patches/" + UUID.nameUUIDFromBytes(baseVersion.toByteArray(Charsets.UTF_8)))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create patch directory")
        state = app.getSharedPreferences("patchrelay-screens", Context.MODE_PRIVATE)
        if (state.getString("base", "") != baseVersion) save(state.edit().clear().putString("base", baseVersion))
    }

    @Synchronized fun start(bundled: String): ScreenProgram {
        workerThread()
        check(!started) { "Start once per process" }
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
        if (active == 0L) return ScreenProgram(0, bundled)
        save(state.edit().putBoolean("booting", true))
        return try { load(active).also { sessionVersion = active } }
        catch (failure: Exception) { restore(bundled, failure) }
    }

    @Synchronized fun stage(url: String): Boolean {
        workerThread()
        val envelope = HttpsDownloads.download(url, 64 * 1024)
        val manifest = verify(envelope)
        val version = manifest.getLong("version")
        if (version <= state.getLong("highest", 0)) return false
        val script = HttpsDownloads.download(manifest.getString("artifactUrl"), MAX_SCRIPT)
        require(script.isNotEmpty()) { "Empty screen patch" }
        ScreenManifests.checkHash(script, manifest.getString("sha256"))
        ScreenManifests.decode(script)
        val target = File(directory, "$version.js")
        val metadata = File(directory, "$version.json")
        // Unaccepted files may remain after an interrupted staging process. Neither is active.
        try {
            write(target, script)
            write(metadata, envelope)
            save(state.edit().putLong("highest", version).putLong("pending", version))
        } catch (failure: Exception) {
            AtomicFile(target).delete(); AtomicFile(metadata).delete()
            throw failure
        }
        return true
    }

    @Synchronized fun markHealthy() {
        check(started)
        if (sessionVersion > 0) save(state.edit().putBoolean("booting", false))
    }

    @Synchronized fun reject(bundled: String, failure: Exception): ScreenProgram {
        workerThread()
        check(started)
        return restore(bundled, failure)
    }

    @Synchronized fun activeVersion() = sessionVersion
    @Synchronized fun pendingVersion() = state.getLong("pending", 0)

    private fun restore(bundled: String, failure: Exception): ScreenProgram {
        Log.e("PatchRelay", "Screen program rejected", failure)
        val previous = state.getLong("previous", 0)
        save(state.edit().putLong("active", previous).putLong("previous", 0).putBoolean("booting", previous > 0))
        if (previous > 0 && previous != sessionVersion) {
            try { return load(previous).also { sessionVersion = previous } }
            catch (fallback: Exception) { Log.e("PatchRelay", "Screen fallback rejected", fallback) }
        }
        sessionVersion = 0
        save(state.edit().putLong("active", 0).putBoolean("booting", false))
        return ScreenProgram(0, bundled)
    }

    private fun load(version: Long): ScreenProgram {
        val metadata = File(directory, "$version.json")
        require(metadata.length() <= 64 * 1024) { "Manifest too large" }
        val manifest = verify(metadata.readBytes())
        require(manifest.getLong("version") == version) { "Version mismatch" }
        val file = File(directory, "$version.js")
        require(file.length() in 1..MAX_SCRIPT.toLong()) { "Invalid program size" }
        val bytes = file.readBytes()
        ScreenManifests.checkHash(bytes, manifest.getString("sha256"))
        return ScreenProgram(version, ScreenManifests.decode(bytes))
    }

    private fun verify(bytes: ByteArray) = ScreenManifests.verify(bytes, publicKey, app.packageName, baseVersion, screenIds)
    private fun save(editor: SharedPreferences.Editor) { if (!editor.commit()) throw IOException("Cannot save screen state") }
    private fun workerThread() { check(Looper.myLooper() != Looper.getMainLooper()) { "Use a worker thread" } }
    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (failure: IOException) { atomic.failWrite(stream); throw failure }
    }
    private companion object { const val MAX_SCRIPT = 2 * 1024 * 1024 }
}
