internal fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32) append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
        }
    }
    append('"')
}

private fun widget(id: String, type: String, fields: String): String =
    "{\"id\":" + jsonString(id) + ",\"type\":" + jsonString(type) + "," + fields + "}"

internal fun text(id: String, value: String, style: String = "body"): String =
    widget(id, "text", "\"text\":" + jsonString(value) + ",\"style\":" + jsonString(style))

internal fun button(id: String, label: String, action: String, enabled: Boolean = true): String =
    widget(id, "button", "\"text\":" + jsonString(label) + ",\"action\":" + jsonString(action) + ",\"enabled\":" + enabled)

internal fun input(id: String, label: String, value: String, action: String): String =
    widget(id, "input", "\"text\":" + jsonString(label) + ",\"value\":" + jsonString(value) + ",\"action\":" + jsonString(action))

internal fun toggle(id: String, label: String, checked: Boolean, action: String): String =
    widget(id, "toggle", "\"text\":" + jsonString(label) + ",\"checked\":" + checked + ",\"action\":" + jsonString(action))

internal fun layout(id: String, type: String, nodes: List<String>): String =
    widget(id, type, "\"children\":[" + nodes.joinToString(",") + "]")

internal fun column(screenId: String, nodes: List<String>, backAction: String? = null): String =
    "{\"screenId\":" + jsonString(screenId) + ",\"type\":\"column\",\"children\":[" + nodes.joinToString(",") + "]" +
        (if (backAction == null) "" else ",\"backAction\":" + jsonString(backAction)) + "}"
