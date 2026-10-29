package dev.patchrelay.demo.patch

import dev.patchrelay.Patch

/** Reports runtime evidence; bundling a library does not imply the host uses it. */
class EmbeddedUpgradePatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        val actual = KotlinVersion.CURRENT.toString()
        val owner = if (KotlinVersion::class.java.classLoader === javaClass.classLoader) "patch" else "parent/app"
        val result = if (actual == "2.0.21") "UPGRADED" else "NOT UPGRADED"
        return "Test 7: existing dependency upgrade\nRequested Kotlin: 2.0.21\nRuntime Kotlin: $actual\nLibrary loader: $owner\nResult: $result"
    }
}
