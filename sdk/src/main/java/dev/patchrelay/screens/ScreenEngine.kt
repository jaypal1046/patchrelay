package dev.patchrelay.screens

import android.content.Context

class ScreenEngine(context: Context, private val screenId: String,
    private val onTree: (ScreenTree) -> Unit, private val onFailure: (Exception) -> Unit) {
    private var closed = false
    private val interpreter = ScreenInterpreter(context) { failure -> closed = true; onFailure(failure) }
    val webView get() = interpreter.webView
    private var busy = true
    private val events = ScreenEvents()

    fun start(script: String) {
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

    private fun dispatch(event: ScreenEvent) {
        busy = true
        evaluate("handleEvent", listOf(screenId, event.action, event.value))
    }

    private fun evaluate(function: String, args: List<String?>) {
        interpreter.call(function, args) { result ->
            val tree = ScreenTrees.parse(result, screenId)
            val next = events.next()
            if (next != null) dispatch(next)
            else { busy = false; onTree(tree) }
        }
    }

    fun close() { closed = true; interpreter.close() }
}
