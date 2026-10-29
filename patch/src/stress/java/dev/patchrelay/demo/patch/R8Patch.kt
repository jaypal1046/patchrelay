package dev.patchrelay.demo.patch

import dev.patchrelay.Patch
import java.util.Locale

class R8Patch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        val formatter = PatchFormatter()
        return "Patch 3: R8/ProGuard\nInput: $input\nOutput: ${formatter.format(input)}\nHelper class: ${formatter.javaClass.name}"
    }
}

class PatchFormatter {
    fun format(input: String): String = input.uppercase(Locale.ROOT).reversed()
}
