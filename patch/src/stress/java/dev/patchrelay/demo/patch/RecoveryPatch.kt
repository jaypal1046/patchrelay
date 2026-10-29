package dev.patchrelay.demo.patch

import dev.patchrelay.Patch

/** Healthy successor to the intentionally failed versions 4 and 5. */
class RecoveryPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return "Patch 6: healthy update\nInput: $input\nCharacters: ${input.length}\nReversed: ${input.reversed()}"
    }
}
