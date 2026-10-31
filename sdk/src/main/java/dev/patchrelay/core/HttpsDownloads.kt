package dev.patchrelay.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Shared bounded HTTPS transport; downloaded bytes are still untrusted. */
object HttpsDownloads {
    @JvmStatic
    @Throws(IOException::class)
    fun download(address: String, limit: Int): ByteArray = download(address, limit) { it.openConnection() as HttpURLConnection }

    internal fun download(address: String, limit: Int, open: (URL) -> HttpURLConnection): ByteArray {
        require(limit > 0) { "Positive download size limit required" }
        var url = URL(address)
        repeat(6) {
            if (url.protocol != "https" || url.userInfo != null) throw IOException("HTTPS URL required")
            val connection = open(url)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect location")
                    url = URL(url, location)
                    return@repeat
                }
                if (status != 200) throw IOException("Download HTTP $status")
                if (connection.contentLengthLong > limit) throw IOException("Download too large")
                connection.inputStream.use { input ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (output.size() + count > limit) throw IOException("Download too large")
                            output.write(buffer, 0, count)
                        }
                        return output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many redirects")
    }

}
