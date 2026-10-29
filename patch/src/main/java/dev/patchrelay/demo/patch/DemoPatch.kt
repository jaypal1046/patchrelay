package dev.patchrelay.demo.patch

import dev.patchrelay.Patch

class DemoPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return "Patched Kotlin logic: hello $input"
    }
}
