package dev.patchrelay.screens

import org.json.JSONObject

sealed class ScreenNode {
    data class Text(val text: String, val heading: Boolean) : ScreenNode()
    data class Button(val text: String, val action: String) : ScreenNode()
}

data class ScreenTree(val screenId: String, val children: List<ScreenNode>)

object ScreenTrees {
    fun parse(json: String, expectedScreen: String): ScreenTree {
        require(json.length <= 65536) { "Screen result too large" }
        val root = JSONObject(json)
        keys(root, setOf("screenId", "type", "children"))
        require(root.getString("screenId") == expectedScreen && root.getString("type") == "column") { "Incompatible screen" }
        val children = root.getJSONArray("children")
        require(children.length() in 1..32) { "Screen must have 1–32 widgets" }
        return ScreenTree(expectedScreen, (0 until children.length()).map { index ->
            val node = children.getJSONObject(index)
            val text = node.getString("text")
            require(text.isNotBlank() && text.length <= 1024) { "Invalid widget text" }
            when (node.getString("type")) {
                "text" -> {
                    keys(node, setOf("type", "text", "heading"))
                    val heading = if (node.has("heading")) node.get("heading") else false
                    require(heading is Boolean) { "Heading must be boolean" }
                    ScreenNode.Text(text, heading)
                }
                "button" -> {
                    keys(node, setOf("type", "text", "action"))
                    val action = node.getString("action")
                    require(action.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Invalid action ID" }
                    ScreenNode.Button(text, action)
                }
                else -> error("Unsupported widget")
            }
        })
    }

    private fun keys(value: JSONObject, allowed: Set<String>) {
        require(value.keys().asSequence().all { it in allowed }) { "Unsupported screen field" }
    }
}
