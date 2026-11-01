package dev.patchrelay.compose

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.serialization.json.*
import kotlin.coroutines.EmptyCoroutineContext

private class RelayNode(val type: String) {
    var id = ""
    var text = ""
    var style = "body"
    var enabled = true
    var value = ""
    var checked = false
    var event: ((String?) -> Unit)? = null
    val children = mutableListOf<RelayNode>()
}

private class RelayApplier(root: RelayNode) : AbstractApplier<RelayNode>(root) {
    override fun insertTopDown(index: Int, instance: RelayNode) { current.children.add(index, instance) }
    override fun insertBottomUp(index: Int, instance: RelayNode) {}
    override fun remove(index: Int, count: Int) { current.children.remove(index, count) }
    override fun move(from: Int, to: Int, count: Int) { current.children.move(from, to, count) }
    override fun onClear() { root.children.clear() }
}

/** Explicit render/event transactions: no frame loop, browser DOM or asynchronous effects. */
@OptIn(ExperimentalComposeApi::class)
class RelayComposition(private val screenId: String, private val backAction: () -> String? = { null },
    private val content: @Composable () -> Unit) {
    private val root = RelayNode("column")
    private val recomposer = Recomposer(EmptyCoroutineContext)
    private val composition = ControlledComposition(RelayApplier(root), recomposer)
    private val observer = Snapshot.registerApplyObserver { values, _ ->
        if (!composition.isDisposed) composition.recordModificationsOf(values)
    }
    private var started = false
    private var active = false
    private var actions = emptyMap<String, RelayNode>()

    fun render(): String = transaction { renderTree() }

    fun event(action: String, value: String? = null): String = transaction {
        require(value == null || value.length <= 1024) { "Compose event value too large" }
        val node = actions[action] ?: error("Unknown Compose action: $action")
        require(node.enabled) { "Disabled Compose action" }
        node.event!!(value)
        renderTree()
    }

    private inline fun transaction(body: () -> String): String {
        check(!composition.isDisposed && !active) { "Compose screen disposed or reentrant" }
        active = true
        try { return body() }
        catch (failure: Throwable) { dispose(); throw failure }
        finally { active = false }
    }

    private fun renderTree(): String {
        if (!started) { started = true; composition.setContent(content) }
        Snapshot.sendApplyNotifications()
        var passes = 0
        while (composition.hasInvalidations) {
            check(++passes <= 16) { "Compose did not settle within 16 passes" }
            val snapshot = Snapshot.takeMutableSnapshot({ composition.recordReadOf(it) }, { composition.recordWriteOf(it) })
            val changed = try { snapshot.enter { composition.recompose() } }
            finally { try { snapshot.apply().check() } finally { snapshot.dispose() } }
            if (changed) { composition.applyChanges(); composition.applyLateChanges() }
            composition.changesApplied()
            Snapshot.notifyObjectsInitialized()
            Snapshot.sendApplyNotifications()
        }
        val ids = mutableSetOf<String>()
        val nextActions = mutableMapOf<String, RelayNode>()
        fun children(node: RelayNode, depth: Int): JsonArray {
            require(node.children.size in 1..32) { "Compose layout requires 1..32 children" }
            return JsonArray(node.children.map { child ->
                require(depth <= 8 && ids.size < 128) { "Compose screen complexity exceeded" }
                require(child.id.matches(Regex("[a-z][a-z0-9_]{0,23}")) && ids.add(child.id)) { "Invalid or duplicate Compose widget ID: ${child.id}" }
                buildJsonObject {
                    put("id", child.id); put("type", child.type)
                    if (child.type in setOf("column", "row", "card")) put("children", children(child, depth + 1))
                    else {
                        require(child.text.isNotBlank() && child.text.length <= 1024) { "Invalid Compose widget text" }
                        put("text", child.text)
                        if (child.type == "text") {
                            require(child.style in setOf("title", "body", "caption", "error"))
                            put("style", child.style)
                        } else {
                            val action = "c_" + child.id
                            nextActions[action] = child
                            put("action", action); put("enabled", child.enabled)
                            if (child.type == "input") { require(child.value.length <= 1024); put("value", child.value) }
                            if (child.type == "toggle") put("checked", child.checked)
                        }
                    }
                }
            })
        }
        val result = buildJsonObject {
            put("screenId", screenId); put("type", "column"); put("children", children(root, 1))
            backAction()?.let { require(it.matches(Regex("[a-z][a-z0-9_-]{0,31}"))); put("backAction", it) }
        }.toString()
        require(result.length <= 65536) { "Compose screen too large" }
        actions = nextActions
        return result
    }

    fun dispose() {
        observer.dispose()
        composition.dispose()
        recomposer.cancel()
        actions = emptyMap()
    }
}

@Composable
private fun Node(type: String, id: String, text: String = "", style: String = "body", enabled: Boolean = true,
    value: String = "", checked: Boolean = false, event: ((String?) -> Unit)? = null,
    content: @Composable () -> Unit = {}) {
    ComposeNode<RelayNode, RelayApplier>(factory = { RelayNode(type) }, update = {
        set(id) { this.id = it }; set(text) { this.text = it }; set(style) { this.style = it }
        set(enabled) { this.enabled = it }; set(value) { this.value = it }; set(checked) { this.checked = it }
        set(event) { this.event = it }
    }, content = content)
}

@Composable fun Text(id: String, text: String, style: String = "body") = Node("text", id, text, style)
@Composable fun Button(id: String, text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Node("button", id, text, enabled = enabled, event = { onClick() })
@Composable fun TextField(id: String, label: String, value: String, enabled: Boolean = true, onValueChange: (String) -> Unit) =
    Node("input", id, label, enabled = enabled, value = value, event = { onValueChange(it ?: error("Missing input value")) })
@Composable fun Toggle(id: String, label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) =
    Node("toggle", id, label, enabled = enabled, checked = checked, event = {
        require(it == "true" || it == "false") { "Invalid toggle value" }; onCheckedChange(it == "true")
    })
@Composable fun Column(id: String, content: @Composable () -> Unit) = Node("column", id, content = content)
@Composable fun Row(id: String, content: @Composable () -> Unit) = Node("row", id, content = content)
@Composable fun Card(id: String, content: @Composable () -> Unit) = Node("card", id, content = content)
