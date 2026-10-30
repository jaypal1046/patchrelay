package dev.patchrelay.screens

import dev.patchrelay.Signatures
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

internal object ScreenManifests {
    fun verify(bytes: ByteArray, key: String, appId: String, base: String, screens: Set<String>, hostHash: String): JSONObject {
        require(bytes.size <= 64 * 1024) { "Manifest too large" }
        val envelope = JSONObject(decode(bytes))
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        Signatures.verify(payload, Base64.getDecoder().decode(envelope.getString("signature")), key)
        val value = JSONObject(decode(payload))
        require(hostHash.matches(Regex("[0-9a-f]{64}")) && value.getString("hostSha256") == hostHash)
        require(value.getInt("schema") == 3 && value.getInt("screenApi") == 2 &&
            value.getString("runtime") == "kotlin-screen-js" && value.getString("appId") == appId &&
            value.getString("baseVersion") == base && value.getLong("version") > 0) { "Incompatible screen patch" }
        val ids = value.getJSONArray("screenIds")
        require(ids.length() in 1..16) { "Invalid screen IDs" }
        val provided = (0 until ids.length()).map { ids.getString(it) }
        require(provided.size == provided.toSet().size && provided.toSet() == screens) { "Unsupported screen contract" }
        require(value.getString("sha256").matches(Regex("[0-9a-f]{64}"))) { "Invalid artifact hash" }
        return value
    }

    fun checkHash(bytes: ByteArray, expected: String) {
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != expected) throw SecurityException("Screen patch hash mismatch")
    }

    fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
