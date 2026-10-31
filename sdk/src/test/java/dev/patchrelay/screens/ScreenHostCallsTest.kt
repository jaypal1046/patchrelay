package dev.patchrelay.screens

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenHostCallsTest {
    private val function = "github.repository.v1"
    private fun request(id: String = "1", name: String = function, argument: String = "JetBrains/kotlin") =
        org.json.JSONObject().put("id", org.json.JSONTokener(id).nextValue()).put("function", name).put("argument", argument).toString()

    @Test fun acceptsOnlyRegisteredOperationsAndMonotonicRequestIds() {
        val calls = ScreenHostCalls.parse("[${request()},${request("2")}]", setOf(function), 0, 4)
        assertEquals(listOf(1, 2), calls.map { it.id })
        assertEquals("JetBrains/kotlin", calls.first().argument)
        for (json in listOf("[${request(name = "android.reflect")}]", "[${request()},${request()}]",
            "[${request("0")}]", "[${request("1.5")}]", "[${request("\"1\"")}]",
            "[${request("2147483648")}]", "[${request(argument = "x".repeat(4097))}]",
            "[${request().dropLast(1)},\"url\":\"https://evil.invalid\"}]")) {
            try { ScreenHostCalls.parse(json, setOf(function), 0, 4); fail("Accepted unsafe host request") }
            catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
        }
        for ((json, last, capacity) in listOf(Triple("[${request()}]", 1, 4), Triple("[${request()}]", 0, 0))) {
            try { ScreenHostCalls.parse(json, setOf(function), last, capacity); fail("Accepted replay/overload") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun hostCompletionsNeverCoalesceOrDisappearAcrossFieldUpdates() {
        val queue = ScreenEvents()
        queue.add(ScreenEvent("", "first", 1))
        queue.add(ScreenEvent("", "second", 2))
        queue.add(ScreenEvent("email", "a"))
        queue.add(ScreenEvent("email", "ab"))
        assertEquals(1, queue.next()!!.hostId)
        assertEquals(2, queue.next()!!.hostId)
        assertEquals("ab", queue.next()!!.value)
        assertNull(queue.next())
    }

    @Test fun runsNativeWorkOffMainThreadAndRedactsFailures() {
        for (fail in listOf(false, true)) {
            val results = mutableListOf<Triple<Int, String?, String?>>()
            val host = ScreenHostCalls(mapOf(function to { argument: String ->
                check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
                if (fail) throw IllegalStateException("private token must not escape")
                argument.uppercase()
            })) { id, result, error -> results.add(Triple(id, result, error)) }
            try {
                host.accept("[${request()}]")
                for (attempt in 0 until 100) {
                    shadowOf(android.os.Looper.getMainLooper()).idle()
                    if (results.isNotEmpty()) break
                    Thread.sleep(5)
                }
                assertEquals(1, results.size)
                assertEquals(1, results.single().first)
                if (fail) assertEquals(Triple(1, null, "Host operation failed"), results.single())
                else assertEquals("JETBRAINS/KOTLIN", results.single().second)
            } finally { host.close() }
        }
    }

    @Test fun timeoutAndCloseSuppressLateCompletions() {
        val waiting = java.util.concurrent.CountDownLatch(1)
        val results = mutableListOf<Triple<Int, String?, String?>>()
        val host = ScreenHostCalls(mapOf(function to { _: String -> waiting.await(); "late" })) {
            id, result, error -> results.add(Triple(id, result, error))
        }
        try {
            host.accept("[${request()}]")
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
            assertEquals(listOf(Triple(1, null, "Host operation timed out")), results)
            host.close()
            waiting.countDown()
            Thread.sleep(20)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, results.size)
        } finally { waiting.countDown(); host.close() }
    }

    @Test fun queuedRequestsExpiredBehindUninterruptibleCallsNeverExecute() {
        val release = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = java.util.concurrent.CountDownLatch(2)
        val executed = java.util.concurrent.atomic.AtomicInteger()
        val replies = mutableListOf<Int>()
        val host = ScreenHostCalls(mapOf(function to { _: String ->
            executed.incrementAndGet()
            entered.countDown()
            // Models a native client which does not finish when Future.cancel interrupts it.
            while (!release.get()) try { Thread.sleep(5) } catch (_: InterruptedException) { }
            "finished"
        })) { id, _, _ -> replies.add(id) }
        try {
            host.accept("[${request()},${request("2")}]")
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
            for (batch in 0 until 10) {
                val first = 3 + batch * 4
                host.accept((first until first + 4).joinToString(prefix = "[", postfix = "]") { request(it.toString()) })
                shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
            }
            assertEquals(42, replies.size)
            assertEquals(2, executed.get())
            release.set(true)
            host.close()
            Thread.sleep(20)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(42, replies.size)
        } finally { release.set(true); host.close() }
    }
}
