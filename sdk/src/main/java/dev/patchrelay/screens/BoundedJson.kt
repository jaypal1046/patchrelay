package dev.patchrelay.screens

import org.json.JSONArray
import org.json.JSONObject

/** Check nesting before Android's recursive JSON parser can exhaust the native thread stack. */
internal object BoundedJson {
    fun objectValue(source: String, limit: Int = 65536): JSONObject {
        check(source, limit)
        return JSONObject(source)
    }
    fun arrayValue(source: String, limit: Int): JSONArray {
        check(source, limit)
        return JSONArray(source)
    }
    private fun check(source: String, limit: Int) {
        require(source.length <= limit) { "JSON too large" }
        var depth = 0
        var quote = '\u0000'
        var escaped = false
        for (character in source) {
            if (quote != '\u0000') {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == quote) quote = '\u0000'
            } else when (character) {
                '"', '\'' -> quote = character
                '{', '[' -> { depth++; require(depth <= 32) { "JSON nesting exceeded" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Unbalanced JSON" } }
            }
        }
        require(depth == 0 && quote == '\u0000') { "Incomplete JSON" }
    }
}
