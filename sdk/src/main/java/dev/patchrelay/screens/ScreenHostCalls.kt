package dev.patchrelay.screens

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Future
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Only host-registered operations execute; patches cannot supply URLs, classes or credentials. */
internal class ScreenHostCalls(private val functions: Map<String, (String) -> String>,
    private val onResult: (Int, String?, String?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4))
    private val active = mutableMapOf<Int, Pair<Future<*>, Runnable>>()
    private var lastId = 0
    private var closed = false

    fun accept(json: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!closed)
        val requests = parse(json, functions.keys, lastId, 4 - active.size)
        for (request in requests) {
            lastId = request.id
            val timeout = Runnable { complete(request.id, null, "Host operation timed out") }
            val future = workers.submit {
                val result = try {
                    functions.getValue(request.function)(request.argument).also {
                        require(it.length <= 4096) { "Host result too large" }
                    }
                } catch (_: Exception) {
                    main.post { complete(request.id, null, "Host operation failed") }
                    return@submit
                }
                main.post { complete(request.id, result, null) }
            }
            active[request.id] = future to timeout
            main.postDelayed(timeout, 30000)
        }
    }

    private fun complete(id: Int, result: String?, error: String?) {
        val (future, timeout) = active.remove(id) ?: return
        main.removeCallbacks(timeout)
        future.cancel(true)
        // Interrupted native I/O may keep both workers busy; do not accumulate cancelled retries.
        (future as? Runnable)?.let(workers::remove)
        if (!closed) onResult(id, result, error)
    }

    fun close() {
        closed = true
        active.values.forEach { (future, timeout) -> future.cancel(true); main.removeCallbacks(timeout) }
        active.clear()
        workers.shutdownNow()
    }

    internal data class Request(val id: Int, val function: String, val argument: String)
    companion object {
        fun parse(json: String, allowed: Set<String>, lastId: Int, capacity: Int): List<Request> {
            val values = BoundedJson.arrayValue(json, 16384)
            require(values.length() <= capacity) { "Too many host operations" }
            var previous = lastId
            return (0 until values.length()).map { index ->
                val value = values.getJSONObject(index)
                require(value.keys().asSequence().toSet() == setOf("id", "function", "argument")) { "Invalid host operation" }
                val id = value.get("id")
                require(id is Int && id > previous) { "Replayed or invalid host request" }
                previous = id
                val function = value.get("function") as? String ?: error("Invalid host function")
                require(function in allowed) { "Unregistered host function" }
                val argument = value.get("argument") as? String ?: error("Invalid host argument")
                require(argument.length <= 4096) { "Host argument too large" }
                Request(id, function, argument)
            }
        }
    }
}
