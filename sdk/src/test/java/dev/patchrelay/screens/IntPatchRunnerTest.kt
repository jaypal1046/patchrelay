package dev.patchrelay.screens

import org.junit.Assert.*
import org.junit.Test

class IntPatchRunnerTest {
    private val total = "checkout.total.v1"
    private val shipping = "checkout.shipping.v1"

    @Test fun routesOnlyMatchedIdsAndRetainsNativeFallback() {
        var jsCalls = 0
        var nativeCalls = 0
        val original: (Int, Int) -> Int = { a, b -> nativeCalls++; a + b }
        val base = IntPatchRunner(setOf(total), "[]") { _, _, _, _ -> error("Unexpected JS call") }
        base.call(total, 3, 100, original) { assertEquals(103, it) }
        val patch = IntPatchRunner(setOf(total, shipping), "[\"$total\"]") { id, a, b, callback ->
            assertEquals(total, id); jsCalls++; callback((a * b).toString())
        }
        patch.call(total, 3, 100, original) { assertEquals(300, it) }
        patch.call(shipping, 10, 5, original) { assertEquals(15, it) }
        assertEquals(2, nativeCalls)
        assertEquals(1, jsCalls)
    }

    @Test fun rejectsWrongBindingsAndResults() {
        for (ids in listOf("[\"unknown\"]", "[\"$total\",\"$total\"]", "[42]")) {
            try { IntPatchRunner(setOf(total), ids) { _, _, _, _ -> }; fail("Accepted wrong ID") }
            catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        }
        for (result in listOf("3.5", "2147483648", "null", "{}")) {
            val runner = IntPatchRunner(setOf(total), "[\"$total\"]") { _, _, _, callback -> callback(result) }
            try { runner.call(total, 3, 100, { _, _ -> error("Must not fall back after JS failure") }) {}; fail("Accepted bad result") }
            catch (_: IllegalStateException) {}
        }
    }
}
