package dev.patchrelay

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import dalvik.system.DexClassLoader
import dev.patchrelay.core.HttpsDownloads
import dev.patchrelay.core.SignedManifests
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Experimental, single-process patch loader for directly distributed Android apps. */
class PatchRelay @Throws(IOException::class) constructor(
    context: Context,
    private val baseVersion: String,
    private val publicKey: String,
) {
    private val context = context.applicationContext
    private val directory: File
    private val state: SharedPreferences
    private var started = false
    private var sessionVersion = 0L

    init {
        require(baseVersion.isNotEmpty()) { "Base version required" }
        directory = File(File(this.context.noBackupFilesDir, "patchrelay"),
            UUID.nameUUIDFromBytes(baseVersion.toByteArray(Charsets.UTF_8)).toString())
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create patch directory")
        state = this.context.getSharedPreferences("patchrelay", Context.MODE_PRIVATE)
        if (baseVersion != state.getString("base", "")) {
            save(state.edit().clear().putString("base", baseVersion))
        }
    }

    /** Run on a worker thread. Stages an update for the next process launch. */
    @Synchronized
    @Throws(Exception::class)
    fun stage(manifestUrl: String): Boolean {
        workerThread()
        val envelope = HttpsDownloads.download(manifestUrl, MAX_MANIFEST)
        val manifest = verify(envelope)
        val version = manifest.getLong("version")
        if (version <= state.getLong("highest", 0)) return false
        val dex = HttpsDownloads.download(manifest.getString("artifactUrl"), MAX_PATCH)
        checkHash(dex, manifest.getString("sha256"))
        val target = File(directory, "$version.dex")
        if (target.exists()) throw IOException("Patch file already exists")
        try {
            FileOutputStream(target).use { stream ->
                // Android 14 requires read-only code files before writing their contents.
                if (!target.setReadOnly()) throw IOException("Cannot protect patch file")
                stream.write(dex)
                stream.fd.sync()
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        try {
            write(File(directory, "$version.json"), envelope)
            save(state.edit().putLong("highest", version).putLong("pending", version))
        } catch (error: Exception) {
            target.delete()
            File(directory, "$version.json").delete()
            throw error
        }
        return true
    }

    /** Call once per process, before invoking patchable logic. */
    @Synchronized
    @Throws(Exception::class)
    fun start(builtIn: Patch): Patch {
        workerThread()
        check(!started) { "start must run once per process" }
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
        if (active == 0L) return builtIn
        // A process that exits before markHealthy rolls back on the next launch.
        save(state.edit().putBoolean("booting", true))
        try {
            val loaded = load(active)
            sessionVersion = active
            return loaded
        } catch (failure: Exception) {
            return restore(active, builtIn, failure)
        } catch (failure: LinkageError) {
            return restore(active, builtIn, failure)
        }
    }

    private fun restore(active: Long, builtIn: Patch, failure: Throwable): Patch {
        val previous = state.getLong("previous", 0)
        save(state.edit().putLong("active", previous).putLong("previous", 0).putBoolean("booting", false))
        if (previous != 0L && previous != active) {
            save(state.edit().putBoolean("booting", true))
            try {
                val loaded = load(previous)
                sessionVersion = previous
                return loaded
            } catch (_: Exception) {
                save(state.edit().putLong("active", 0).putBoolean("booting", false))
            } catch (_: LinkageError) {
                save(state.edit().putLong("active", 0).putBoolean("booting", false))
            }
        }
        Log.e("PatchRelay", "Rejected patch; restored fallback", failure)
        return builtIn
    }

    /** Confirm only after exercising the patched startup path successfully. */
    @Synchronized
    @Throws(IOException::class)
    fun markHealthy() {
        check(started) { "Call start first" }
        if (sessionVersion != 0L) save(state.edit().putBoolean("booting", false))
    }

    @Synchronized
    fun activeVersion(): Long = sessionVersion

    @Synchronized
    fun pendingVersion(): Long = state.getLong("pending", 0)

    private fun load(version: Long): Patch {
        val manifest = verify(File(directory, "$version.json").readBytes())
        if (manifest.getLong("version") != version) throw SecurityException("Version mismatch")
        val dex = File(directory, "$version.dex")
        if (dex.length() > MAX_PATCH) throw SecurityException("Patch too large")
        checkHash(dex.readBytes(), manifest.getString("sha256"))
        val loader = DexClassLoader(dex.absolutePath, context.codeCacheDir.absolutePath, null, Patch::class.java.classLoader)
        val implementation = loader.loadClass(manifest.getString("entryPoint"))
        if (implementation.classLoader !== loader) throw SecurityException("Entry point must come from patch")
        return implementation.asSubclass(Patch::class.java).getDeclaredConstructor().newInstance()
    }

    private fun verify(envelope: ByteArray): JSONObject =
        SignedManifests.verify(envelope, publicKey, context.packageName, baseVersion)

    private fun checkHash(data: ByteArray, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        if (hex != expected) throw SecurityException("Patch hash mismatch")
    }

    private fun write(file: File, data: ByteArray) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(data); atomic.finishWrite(output) }
        catch (failure: IOException) { atomic.failWrite(output); throw failure }
    }

    private fun save(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Cannot persist patch state")
    }

    private fun workerThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Use a worker thread" }
    }

    private companion object {
        const val MAX_MANIFEST = 64 * 1024
        const val MAX_PATCH = 16 * 1024 * 1024
    }
}
