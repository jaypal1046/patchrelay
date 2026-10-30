package dev.patchrelay.screens

import org.json.JSONObject

data class ScreenNode(val id: String, val type: String, val text: String = "",
    val action: String = "", val value: String = "", val enabled: Boolean = true,
    val checked: Boolean = false, val style: String = "body", val children: List<ScreenNode> = emptyList())

data class ScreenTree(val screenId: String, val children: List<ScreenNode>)

/** API 2: bounded data only; widgets cannot request native APIs. */
object ScreenTrees {
    fun parse(json: String, expectedScreen: String): ScreenTree {
        require(json.length <= 65536) { "Screen result too large" }
        val root = JSONObject(json)
        keys(root, setOf("screenId", "type", "children"))
        require(string(root, "screenId") == expectedScreen && string(root, "type") == "column") { "Incompatible screen" }
        val ids = mutableSetOf<String>()
        fun node(value: JSONObject, depth: Int): ScreenNode {
            require(depth <= 8 && ids.size < 128) { "Screen complexity exceeded" }
            val id = string(value, "id")
            require(id.matches(Regex("[a-z][a-z0-9_-]{0,63}")) && ids.add(id)) { "Invalid or duplicate widget ID" }
            val type = string(value, "type")
            if (type in setOf("column", "row", "card")) {
                keys(value, setOf("id", "type", "children"))
                val children = value.getJSONArray("children")
                require(children.length() in 1..32) { "Layout must have 1–32 widgets" }
                return ScreenNode(id, type, children = (0 until children.length()).map { node(children.getJSONObject(it), depth + 1) })
            }
            val common = setOf("id", "type", "text")
            val allowed = when (type) {
                "text" -> common + "style"
                "button" -> common + setOf("action", "enabled")
                "input" -> common + setOf("action", "value", "enabled")
                "toggle" -> common + setOf("action", "checked", "enabled")
                else -> error("Unsupported widget")
            }
            keys(value, allowed)
            val text = string(value, "text")
            require(text.isNotBlank() && text.length <= 1024) { "Invalid widget text" }
            val action = if (type == "text") "" else string(value, "action").also {
                require(it.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Invalid action ID" }
            }
            val style = if (value.has("style")) string(value, "style") else "body"
            require(style in setOf("title", "body", "caption", "error")) { "Unsupported text style" }
            val content = if (type == "input") string(value, "value") else ""
            require(content.length <= 1024) { "Input too long" }
            return ScreenNode(id, type, text, action, content, boolean(value, "enabled", true),
                boolean(value, "checked", false), style)
        }
        val children = root.getJSONArray("children")
        require(children.length() in 1..32) { "Screen must have 1–32 widgets" }
        return ScreenTree(expectedScreen, (0 until children.length()).map { node(children.getJSONObject(it), 1) })
    }

    private fun string(value: JSONObject, key: String): String = value.get(key) as? String ?: error("Expected string: $key")
    private fun boolean(value: JSONObject, key: String, default: Boolean): Boolean =
        if (value.has(key)) value.get(key) as? Boolean ?: error("Expected boolean: $key") else default
    private fun keys(value: JSONObject, allowed: Set<String>) {
        require(value.keys().asSequence().all { it in allowed }) { "Unsupported screen field" }
    }
}
