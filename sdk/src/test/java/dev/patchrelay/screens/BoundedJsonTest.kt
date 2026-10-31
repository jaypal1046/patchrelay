package dev.patchrelay.screens

import org.junit.Assert.*
import org.junit.Test

class BoundedJsonTest {
    @Test fun rejectsExcessiveNestingBeforeRecursiveParsing() {
        for (source in listOf("[".repeat(10000) + "0" + "]".repeat(10000),
            "{\"x\":".repeat(1000) + "0" + "}".repeat(1000))) {
            try { BoundedJson.arrayValue(source, 65536); fail("Accepted nested JSON") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun countsStructureWithoutCountingEscapedStringContent() {
        val value = "braces [ { \\" + "\"" + " } ] ".repeat(500)
        val json = org.json.JSONObject().put("text", value).toString()
        assertEquals(value, BoundedJson.objectValue(json).getString("text"))
        assertEquals(1, BoundedJson.arrayValue("[".repeat(32) + "0" + "]".repeat(32), 100).length())
        try { BoundedJson.objectValue("{}", 1); fail("Accepted oversized JSON") }
        catch (_: IllegalArgumentException) { }
    }
}
