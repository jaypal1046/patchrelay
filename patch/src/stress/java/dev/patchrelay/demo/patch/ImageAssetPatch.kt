package dev.patchrelay.demo.patch

import android.os.Process
import android.system.Os
import dev.patchrelay.Patch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Signed code pins the external PNG's hash; image bytes are not bundled in the APK. */
class ImageAssetPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return try { applyImage() } catch (error: IOException) {
            "Image not downloaded: ${error.message}\nForce stop and reopen to retry. Existing image kept."
        }
    }

    private fun applyImage(): String {
        val root = File(System.getProperty("java.io.tmpdir") ?: error("Cache unavailable")).canonicalFile
        check(Os.stat(root.path).st_uid == Process.myUid()) { "Cache is not owned by this app" }
        val target = File(root, "patchrelay-preview.png").canonicalFile
        check(target.parentFile == root) { "Image path escapes cache" }
        val expected = BuildConfig.TEST_IMAGE_SHA256
        if (target.exists()) {
            check(target.length() <= MAX_BYTES && hash(target.readBytes()) == expected) { "Existing image differs; left untouched" }
        } else {
            val bytes = download()
            check(hash(bytes) == expected) { "Image hash mismatch" }
            check(bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) { "Expected PNG image" }
            val temporary = Files.createTempFile(root.toPath(), ".patchrelay-image-", ".tmp")
            try {
                FileOutputStream(temporary.toFile()).use { output -> output.write(bytes); output.fd.sync() }
                Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        return "GitHub image verified\nSHA-256: ${hash(target.readBytes())}"
    }

    private fun download(): ByteArray {
        var url = URL("https://github.com/jaypal1046/patchrelay/releases/download/image-assets-test-v1/after.png")
        repeat(6) {
            check(url.protocol == "https" && url.userInfo == null) { "HTTPS required" }
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect"))
                    return@repeat
                }
                if (status != 200) throw IOException("Image HTTP $status")
                if (connection.contentLengthLong > MAX_BYTES) throw IOException("Image too large")
                connection.inputStream.use { input ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (output.size() + count > MAX_BYTES) throw IOException("Image too large")
                            output.write(buffer, 0, count)
                        }
                        return output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many redirects")
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object { const val MAX_BYTES = 512 * 1024 }
}
