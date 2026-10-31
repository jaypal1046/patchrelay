package dev.patchrelay.screens

import dev.patchrelay.Signatures
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

internal object ScreenManifests {
    fun verify(bytes: ByteArray, key: String, appId: String, base: String, screens: Set<String>, hostHash: String): JSONObject {
        return verify(bytes, setOf(key), appId, base, screens, hostHash)
    }

    fun verify(bytes: ByteArray, keys: Set<String>, appId: String, base: String, screens: Set<String>, hostHash: String): JSONObject {
        val value = verifiedPayload(bytes, keys)
        val version = value.get("version")
        require((version is Long || version is Int) && (version as Number).toLong() > 0) { "Invalid patch version" }
        require(hostHash.matches(Regex("[0-9a-f]{64}")) && value.getString("hostSha256") == hostHash)
        require(value.getInt("schema") == 3 && value.getInt("screenApi") == 2 &&
            value.getString("runtime") == "kotlin-screen-js" && value.getString("appId") == appId &&
            value.getString("baseVersion") == base) { "Incompatible screen patch" }
        val ids = value.getJSONArray("screenIds")
        require(ids.length() in 1..16) { "Invalid screen IDs" }
        val provided = (0 until ids.length()).map { ids.getString(it) }
        require(provided.size == provided.toSet().size && provided.toSet() == screens) { "Unsupported screen contract" }
        require(value.getString("sha256").matches(Regex("[0-9a-f]{64}"))) { "Invalid artifact hash" }
        return value
    }

    fun verifiedPayload(bytes: ByteArray, keys: Set<String>): JSONObject {
        require(keys.size in 1..8) { "Configure 1 to 8 trusted signing keys" }
        require(bytes.size <= 64 * 1024) { "Manifest too large" }
        val envelope = BoundedJson.objectValue(decode(bytes))
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val signature = Base64.getDecoder().decode(envelope.getString("signature"))
        if (keys.none { key ->
            try { Signatures.verify(payload, signature, key); true }
            catch (_: java.security.GeneralSecurityException) { false }
            catch (_: IllegalArgumentException) { false }
            catch (_: SecurityException) { false }
        }) throw SecurityException("No trusted key signed this patch")
        return BoundedJson.objectValue(decode(payload))
    }

    fun checkHash(bytes: ByteArray, expected: String) {
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != expected) throw SecurityException("Screen patch hash mismatch")
    }

    fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
