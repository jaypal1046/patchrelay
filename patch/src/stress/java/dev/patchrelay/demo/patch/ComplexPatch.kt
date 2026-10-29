package dev.patchrelay.demo.patch

import com.google.gson.GsonBuilder
import dev.patchrelay.Patch
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

/** Test fixture: exercises dependencies, archive resources, and app-private data. */
class ComplexPatch : Patch {
    override fun invoke(operation: String, input: String): String = when (operation) {
        "newDependency" -> DigestUtils.sha256Hex(input)
        "dependencyVersion" -> Class.forName("com.google.gson.internal.GsonBuildConfig")
            .getField("VERSION").get(null).toString()
        "newDependencyApi" -> GsonBuilder().disableJdkUnsafe().create().toJson(input)
        "readPatchAsset" -> javaClass.classLoader!!.getResourceAsStream("catalog.json")
            ?.bufferedReader()?.use { it.readText() } ?: "asset-not-present-in-dex"
        "writeFile" -> {
            File(input).writeText("{\"revision\":2,\"title\":\"Updated private file\"}")
            "written"
        }
        "greet" -> "Complex Kotlin patch: hello $input"
        else -> error("Unknown operation: $operation")
    }
}
