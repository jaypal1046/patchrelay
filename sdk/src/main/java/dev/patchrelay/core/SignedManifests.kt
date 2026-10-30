package dev.patchrelay.core

import dev.patchrelay.Signatures
import org.json.JSONObject
import java.util.Base64

/** Verify signed bytes before interpreting compatibility metadata. */
object SignedManifests {
    @JvmStatic
    fun verify(envelope: ByteArray, publicKey: String, appId: String, baseVersion: String): JSONObject {
        if (envelope.size > 64 * 1024) throw SecurityException("Manifest too large")
        val signed = JSONObject(String(envelope, Charsets.UTF_8))
        val payload = Base64.getDecoder().decode(signed.getString("payload"))
        Signatures.verify(payload, Base64.getDecoder().decode(signed.getString("signature")), publicKey)
        val manifest = JSONObject(String(payload, Charsets.UTF_8))
        if (manifest.getInt("schema") != 1 || manifest.getInt("apiVersion") != 1 ||
            appId != manifest.getString("appId") || baseVersion != manifest.getString("baseVersion") ||
            manifest.getLong("version") <= 0
        ) throw SecurityException("Patch incompatible with installed app")
        return manifest
    }
}
