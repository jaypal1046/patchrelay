package dev.patchrelay.screens

internal data class ScreenEvent @JvmOverloads constructor(val action: String, val value: String?, val hostId: Int? = null, val error: String? = null)

internal class ScreenEvents {
    private val events = java.util.ArrayDeque<ScreenEvent>()
    fun add(event: ScreenEvent) {
        if (event.hostId == null && events.peekLast()?.hostId == null && event.value != null &&
            events.peekLast()?.action == event.action && events.peekLast()?.value != null) events.removeLast()
        require(events.size < 64) { "Too many pending screen events" }
        events.addLast(event)
    }
    fun next(): ScreenEvent? = events.pollFirst()
}
