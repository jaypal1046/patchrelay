package dev.patchrelay.demo.patch

import dev.patchrelay.Patch
import org.apache.commons.codec.digest.DigestUtils

/** Identical code is packaged both without and with the library to isolate the cause. */
class MissingDependencyPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return try {
            val hash = DigestUtils.sha256Hex(input)
            "Test 5: dependency packaging\nResult: AVAILABLE\nInput: $input\nSHA-256: $hash"
        } catch (error: NoClassDefFoundError) {
            "Test 5: dependency packaging\nResult: MISSING\n${error.javaClass.simpleName}: ${error.message}"
        }
    }
}
