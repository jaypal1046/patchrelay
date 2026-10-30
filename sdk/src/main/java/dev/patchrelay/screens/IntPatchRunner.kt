package dev.patchrelay.screens

import org.json.JSONArray

/** Proof contract: prepared (Int, Int) -> Int functions, called asynchronously on the UI thread. */
class IntPatchRunner internal constructor(registered: Set<String>, json: String,
    private val invoke: (String, Int, Int, (String) -> Unit) -> Unit) {
    private val registered = registered.toSet()
    private val patched: Set<String>
    init {
        require(registered.size in 1..64 && registered.all { it.matches(Regex("[a-z][a-z0-9_.-]{0,95}")) })
        require(json.length <= 8192)
        val ids = JSONArray(json)
        require(ids.length() <= registered.size) { "Too many function bindings" }
        val values = (0 until ids.length()).map { ids.get(it) as? String ?: error("Invalid function ID") }
        require(values.size == values.toSet().size && registered.containsAll(values)) { "Unknown or duplicate function binding" }
        patched = values.toSet()
    }

    fun isPatched(id: String): Boolean { require(id in registered); return id in patched }

    fun call(id: String, first: Int, second: Int, original: (Int, Int) -> Int, onResult: (Int) -> Unit) {
        require(id in registered) { "Unregistered function" }
        if (id !in patched) onResult(original(first, second))
        else invoke(id, first, second) { result ->
            val value = result.toIntOrNull() ?: error("Patched function must return a 32-bit integer")
            onResult(value)
        }
    }
}
