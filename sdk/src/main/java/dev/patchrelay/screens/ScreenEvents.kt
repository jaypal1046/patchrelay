package dev.patchrelay.screens

internal data class ScreenEvent(val action: String, val value: String?)

internal class ScreenEvents {
    private val events = java.util.ArrayDeque<ScreenEvent>()
    fun add(event: ScreenEvent) {
        if (event.value != null && events.peekLast()?.action == event.action && events.peekLast()?.value != null) events.removeLast()
        require(events.size < 64) { "Too many pending screen events" }
        events.addLast(event)
    }
    fun next(): ScreenEvent? = events.pollFirst()
}
