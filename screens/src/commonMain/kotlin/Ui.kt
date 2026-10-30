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

internal fun text(value: String, heading: Boolean = false): String =
    "{\"type\":\"text\",\"text\":" + jsonString(value) + ",\"heading\":" + heading + "}"

internal fun button(label: String, action: String): String =
    "{\"type\":\"button\",\"text\":" + jsonString(label) + ",\"action\":" + jsonString(action) + "}"

internal fun column(screenId: String, nodes: List<String>): String =
    "{\"screenId\":" + jsonString(screenId) + ",\"type\":\"column\",\"children\":[" + nodes.joinToString(",") + "]}"
