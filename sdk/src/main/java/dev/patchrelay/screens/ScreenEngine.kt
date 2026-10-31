package dev.patchrelay.screens

import android.content.Context

class ScreenEngine(context: Context, private val screenId: String,
    private val onTree: (ScreenTree) -> Unit, private val onFailure: (Exception) -> Unit) {
    private var closed = false
    private val interpreter = ScreenInterpreter(context) { failure -> close(); onFailure(failure) }
    val webView get() = interpreter.webView
    private var busy = true
    private var backAction: String? = null
    private val events = ScreenEvents()
    private var started = false
    private val hostFunctions = mutableMapOf<String, (String) -> String>()
    private var hostCalls: ScreenHostCalls? = null

    /** Register before start. Runs on SDK workers; validate arguments and keep credentials native. */
    fun registerHostFunction(id: String, function: (String) -> String) {
        check(!started && !closed) { "Register host functions before start" }
        require(id.matches(Regex("[a-z][a-z0-9_.-]{0,95}")) && id !in hostFunctions && hostFunctions.size < 32)
        hostFunctions[id] = function
    }

    fun start(script: String) {
        check(!started && !closed)
        started = true
        if (hostFunctions.isNotEmpty()) hostCalls = ScreenHostCalls(hostFunctions.toMap()) { id, result, error ->
            if (!closed) try {
                events.add(ScreenEvent("", result, id, error))
                if (!busy) dispatch(events.next()!!)
            } catch (failure: Exception) { close(); onFailure(failure) }
        }
        interpreter.start(script) { evaluate("renderScreen", listOf(screenId)) }
    }

    fun event(action: String, value: String? = null): Boolean {
        if (closed) return false
        try {
            require(action.matches(Regex("[a-z][a-z0-9_-]{0,31}")) && (value == null || value.length <= 1024))
            events.add(ScreenEvent(action, value))
            if (!busy) dispatch(events.next()!!)
        } catch (failure: Exception) { close(); onFailure(failure) }
        return true
    }

    fun prepareIntFunctions(ids: Set<String>, onReady: (IntPatchRunner) -> Unit) {
        interpreter.call("patchedFunctionIds", emptyList()) { json ->
            onReady(IntPatchRunner(ids, json) { id, first, second, result ->
                interpreter.call("invokePatch", listOf(id, first, second), result)
            })
        }
    }

    /** True when handled/awaiting a page update; false delegates root Back to Android. */
    fun back(): Boolean {
        if (closed) return false
        if (busy) return true
        return backAction?.let { event(it) } ?: false
    }

    private fun dispatch(event: ScreenEvent) {
        busy = true
        if (event.hostId != null) evaluate("handleHostResult", listOf(screenId, event.hostId, event.value, event.error))
        else evaluate("handleEvent", listOf(screenId, event.action, event.value))
    }

    private fun evaluate(function: String, args: List<Any?>) {
        interpreter.call(function, args) { result ->
            val tree = ScreenTrees.parse(result, screenId)
            if (hostCalls == null) finish(tree)
            else interpreter.call("takeHostRequests", emptyList()) { requests ->
                hostCalls!!.accept(requests)
                finish(tree)
            }
        }
    }

    private fun finish(tree: ScreenTree) {
        val next = events.next()
        if (next != null) dispatch(next)
        else { busy = false; backAction = tree.backAction; onTree(tree) }
    }

    fun close() { closed = true; hostCalls?.close(); interpreter.close() }
}
