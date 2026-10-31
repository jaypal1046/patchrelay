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
class ScreenUpdates internal constructor(context: Context, private val baseVersion: String, publicKeys: Set<String>,
    screenIds: Set<String>, private val hostHash: String, private val download: (String, Int) -> ByteArray) {
    constructor(context: Context, baseVersion: String, publicKey: String, screenIds: Set<String>, hostHash: String) :
        this(context, baseVersion, setOf(publicKey), screenIds, hostHash, HttpsDownloads::download)

    /** Host-owned trust set: overlap keys during rotation; omit revoked keys in the next host release. */
    constructor(context: Context, baseVersion: String, publicKeys: Set<String>, screenIds: Set<String>, hostHash: String) :
        this(context, baseVersion, publicKeys, screenIds, hostHash, HttpsDownloads::download)

    private val publicKeys = publicKeys.toSet()
    private val screenIds = screenIds.toSet()
    private val app = context.applicationContext
    private val directory: File
    private val state: SharedPreferences
    private var started = false
    private var sessionVersion = 0L

    init {
        require(baseVersion.isNotBlank() && screenIds.isNotEmpty() && hostHash.matches(Regex("[0-9a-f]{64}")))
        require(publicKeys.size in 1..8 && publicKeys.none(String::isBlank))
        val identity = "$baseVersion:$hostHash"
        directory = File(app.noBackupFilesDir, "screen-patches/" + UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create patch directory")
        state = app.getSharedPreferences("patchrelay-screens", Context.MODE_PRIVATE)
        if (state.getString("base", "") != identity) save(state.edit().clear().putString("base", identity))
    }

    @Synchronized fun start(bundled: String): ScreenProgram {
        workerThread()
        check(!started) { "Start once per process" }
        started = true
        if (!state.getBoolean("enabled", true)) return ScreenProgram(0, bundled)
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
        if (!state.getBoolean("enabled", true)) return false
        val envelope = download(url, 64 * 1024)
        val manifest = verify(envelope)
        val version = manifest.getLong("version")
        if (version <= state.getLong("highest", 0)) return false
        val script = download(manifest.getString("artifactUrl"), MAX_SCRIPT)
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
        workerThread()
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

    /** Host must close the old engine and render the returned bundled program. Keeps replay protection. */
    @Synchronized fun disable(bundled: String): ScreenProgram {
        workerThread()
        save(state.edit().putBoolean("enabled", false).putLong("active", 0).putLong("previous", 0)
            .remove("pending").putBoolean("booting", false))
        sessionVersion = 0
        return ScreenProgram(0, bundled)
    }

    /** Explicit host decision; disabled versions do not become active again. */
    @Synchronized fun enable() { workerThread(); save(state.edit().putBoolean("enabled", true)) }

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
        val manifest = verify(read(metadata, 64 * 1024))
        require(manifest.getLong("version") == version) { "Version mismatch" }
        val file = File(directory, "$version.js")
        val bytes = read(file, MAX_SCRIPT)
        require(bytes.isNotEmpty()) { "Empty screen patch" }
        ScreenManifests.checkHash(bytes, manifest.getString("sha256"))
        return ScreenProgram(version, ScreenManifests.decode(bytes))
    }

    private fun verify(bytes: ByteArray) = ScreenManifests.verify(bytes, publicKeys, app.packageName, baseVersion, screenIds, hostHash)
    private fun read(file: File, limit: Int): ByteArray = AtomicFile(file).openRead().use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= limit) { "Saved patch file too large" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
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
