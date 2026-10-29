package dev.patchrelay.demo.patch

import android.os.Process
import android.system.Os
import dev.patchrelay.Patch
import java.io.File

class FileChangePatch : Patch {
    override fun invoke(operation: String, input: String): String {
        require(operation == "greet") { "Unknown operation: $operation" }
        return try {
            val path = System.getProperty("java.io.tmpdir") ?: error("App cache path unavailable")
            val directory = File(path).canonicalFile
            check(Os.stat(directory.path).st_uid == Process.myUid()) { "Cache directory is not owned by this app" }
            "Test 4: real file contents\n${FileProbeStore.update(directory)}\nResult: written and read back"
        } catch (error: Exception) {
            "Test 4: file update blocked\n${error.javaClass.simpleName}: ${error.message}"
        }
    }
}
