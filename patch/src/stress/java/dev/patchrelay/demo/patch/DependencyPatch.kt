package dev.patchrelay.demo.patch

import dev.patchrelay.Patch
import org.apache.commons.codec.digest.DigestUtils

/** Real library call: Codec is shipped in this patch, not in the installed demo APK. */
class DependencyPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return "Patch 2: new dependency\nApache Commons Codec\nInput: $input\nSHA-256: ${DigestUtils.sha256Hex(input)}"
    }
}
