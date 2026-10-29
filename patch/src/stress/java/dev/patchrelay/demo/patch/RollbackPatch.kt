package dev.patchrelay.demo.patch

import dev.patchrelay.Patch

/** Deliberate, caught startup failure. No file writes or other side effects. */
class RollbackPatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        error("Patch 5: repeated controlled startup failure.\nRollback test: restart once more to restore patch 3.")
    }
}
