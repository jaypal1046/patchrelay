package dev.patchrelay.screens

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Opt-in local program state, isolated by installed app/base/fingerprint; never executable code. */
class ScreenStateStore(context: Context, baseVersion: String, hostSha256: String) {
    private val file: AtomicFile
    init {
        require(baseVersion.isNotEmpty() && hostSha256.matches(Regex("[a-f0-9]{64}")))
        val route = "${context.packageName}:$baseVersion:$hostSha256"
        val hash = MessageDigest.getInstance("SHA-256").digest(route.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        file = AtomicFile(File(context.noBackupFilesDir, "screen-state/$hash.json"))
    }
    @Synchronized fun load(): String? = try {
        file.openRead().use { input ->
            val bytes = ByteArray(32769)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            require(count <= 32768)
            String(bytes, 0, count, Charsets.UTF_8).also(::validate)
        }
    } catch (_: java.io.IOException) { null }
      catch (_: IllegalArgumentException) { null }
      catch (_: org.json.JSONException) { null }

    @Synchronized fun save(snapshot: String) {
        validate(snapshot)
        val stream = file.startWrite()
        try { stream.write(snapshot.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private fun validate(snapshot: String) {
        val root = BoundedJson.objectValue(snapshot, 8192)
        require(root.length() == 2 && root.getString("schema").matches(Regex("[a-f0-9]{64}")))
        val values = root.getJSONObject("values")
        require(values.length() <= 32)
        values.keys().forEach { key ->
            require(key.matches(Regex("[a-z][a-z0-9_.-]{0,95}")))
            val value = values.get(key)
            require(value == JSONObject.NULL || value is Boolean || value is Number && value.toDouble().isFinite() || value is String && value.length <= 4096)
        }
    }
}
