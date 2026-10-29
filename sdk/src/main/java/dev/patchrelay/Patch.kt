package dev.patchrelay

/** Stable boundary shared by the installed app and separately compiled patches. */
fun interface Patch {
    @Throws(Exception::class)
    fun invoke(operation: String, input: String): String
}
