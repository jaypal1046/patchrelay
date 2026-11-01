package dev.patchrelay.screens

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Scalar JSON transport; all allowed IDs and signatures come from installed hooks. */
class FunctionPatchRunner internal constructor(
    registered: Map<String, String>, json: String,
    private val invoke: (String, String, (String) -> Unit) -> Unit,
    private val onFailure: (Exception) -> Unit = { throw it },
    private val deliver: (() -> Unit) -> Unit = { it() }
) {
    private val contracts = registered.mapValues { (_, signature) ->
        val match = Regex("(?:suspend )?\\(([^)]*)\\)->(Int|Double|Boolean|String|Int\\?|Double\\?|Boolean\\?|String\\?|Unit)").matchEntire(signature)
            ?: error("Unsupported installed function signature")
        val arguments = match.groupValues[1].let { if (it.isEmpty()) emptyList() else it.split(',') }
        require(arguments.size <= 8 && arguments.all { it in scalarTypes })
        arguments to match.groupValues[2]
    }
    private val patched: Set<String>
    private val pending = mutableSetOf<(Result<String>) -> Unit>()
    private var stopped: Exception? = null
    init {
        require(registered.size <= 64 && registered.keys.all { it.matches(Regex("[a-z][a-z0-9_.-]{0,95}")) })
        val bindings = BoundedJson.objectValue(json, 16384)
        require(bindings.length() <= registered.size)
        patched = bindings.keys().asSequence().map { id ->
            require(id in registered && bindings.get(id) == registered[id]) { "Unknown or incompatible function binding: $id" }
            id
        }.toSet()
    }

    fun call(id: String, arguments: String, original: ((Result<String>) -> Unit) -> Unit, onResult: (String) -> Unit) {
        callSuspending(id, arguments, original) { result ->
            if (result.isFailure && synchronized(pending) { stopped != null }) return@callSuspending
            try { onResult(result.getOrThrow()) } catch (failure: Exception) { onFailure(failure) }
        }
    }

    /** Result-bearing transport lets compiler-instrumented suspend calls resume on failure. */
    fun callSuspending(id: String, arguments: String, original: ((Result<String>) -> Unit) -> Unit, onResult: (Result<String>) -> Unit) {
        val contract = contracts[id] ?: error("Unregistered function")
        val args = BoundedJson.arrayValue(arguments, 8192)
        require(args.length() == contract.first.size) { "Wrong argument count" }
        contract.first.forEachIndexed { index, type -> validate(args.get(index), type) }
        val completed = AtomicBoolean()
        lateinit var finish: (Result<String>) -> Unit
        finish = { result ->
            if (completed.compareAndSet(false, true)) {
                synchronized(pending) { pending.remove(finish) }
                deliver {
                    val checked = runCatching {
                        val text = result.getOrThrow()
                        val value = BoundedJson.objectValue("{\"value\":$text}", 8204)
                        require(text.length <= 8192 && value.length() == 1)
                        validate(value.get("value"), contract.second)
                        text
                    }
                    onResult(checked)
                }
            }
        }
        val failure = synchronized(pending) {
            stopped.also { if (it == null) { check(pending.size < 64) { "Too many pending function calls" }; pending.add(finish) } }
        }
        if (failure != null) { finish(Result.failure(failure)); return }
        try {
            if (id in patched) invoke(id, arguments) { finish(Result.success(it)) }
            else original(finish)
        } catch (failure: Exception) {
            if (!completed.get()) finish(Result.failure(failure)) else throw failure
        }
    }

    internal fun close(failure: Exception) {
        val callbacks = synchronized(pending) { stopped = failure; pending.toList() }
        callbacks.forEach { it(Result.failure(failure)) }
    }

    private fun validate(value: Any, type: String) {
        if (value == JSONObject.NULL) { require(type.endsWith('?') || type == "Unit") { "Unexpected null result" }; return }
        require(when (type.removeSuffix("?")) {
            "Int" -> value is Int || value is Long && value in Int.MIN_VALUE..Int.MAX_VALUE
            "Double" -> value is Number && value.toDouble().isFinite()
            "Boolean" -> value is Boolean
            "String" -> value is String && value.length <= 4096
            else -> false
        }) { "Value does not match $type" }
    }

    private companion object {
        val scalarTypes = setOf("Int", "Double", "Boolean", "String", "Int?", "Double?", "Boolean?", "String?")
    }
}
