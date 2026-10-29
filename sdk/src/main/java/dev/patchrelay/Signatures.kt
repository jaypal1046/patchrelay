package dev.patchrelay

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Signatures {
    @JvmStatic
    @Throws(Exception::class)
    fun verify(payload: ByteArray, signature: ByteArray, publicKey: String) {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update(payload)
        if (!verifier.verify(signature)) throw SecurityException("Invalid patch signature")
    }
}
