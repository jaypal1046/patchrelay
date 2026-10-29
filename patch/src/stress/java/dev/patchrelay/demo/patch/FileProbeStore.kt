package dev.patchrelay.demo.patch

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileProbeStore {
    const val FILE_NAME = "patchrelay-file-test.json"
    private const val INITIAL = "{\"patchrelayTest\":4,\"revision\":1}"
    private const val UPDATED = "{\"patchrelayTest\":4,\"revision\":2}"

    fun update(directory: File): String {
        val root = directory.canonicalFile
        require(root.isDirectory) { "Test directory unavailable" }
        val target = File(root, FILE_NAME).canonicalFile
        require(target.parentFile == root) { "Test file escapes its directory" }
        if (!target.exists()) replace(root, target, INITIAL)
        val before = target.readText(Charsets.UTF_8)
        check(before == INITIAL || before == UPDATED) { "Unrecognized existing file left untouched" }
        replace(root, target, UPDATED)
        val after = target.readText(Charsets.UTF_8)
        check(after == UPDATED) { "File readback differs from written bytes" }
        return "File: ${target.path}\nBefore: $before\nAfter: $after"
    }

    private fun replace(root: File, target: File, content: String) {
        val temporary = Files.createTempFile(root.toPath(), ".patchrelay-file-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
