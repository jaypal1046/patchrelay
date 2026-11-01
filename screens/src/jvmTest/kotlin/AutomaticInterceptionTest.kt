import kotlin.coroutines.*
import kotlin.test.*

class AutomaticInterceptionTest {
    private fun <T> launch(body: suspend () -> T, done: (Result<T>) -> Unit) {
        body.startCoroutine(Continuation(EmptyCoroutineContext, done))
    }

    @Test fun directCallsUseNativeThenPatchedThenNativeAgain() {
        try {
            launch({ checkoutStatus(false) }) { assertEquals("Pending", it.getOrThrow()) }
            PatchRelayFunctions.installDispatcher { id, args, _, done ->
                assertEquals("checkout.status.v1", id)
                assertEquals("[false]", args)
                done(Result.success("\"Patched\""))
            }
            launch({ checkoutStatus(false) }) { assertEquals("Patched", it.getOrThrow()) }
            PatchRelayFunctions.installDispatcher { _, _, original, done -> original(done) }
            launch({ checkoutStatus(true) }) { assertEquals("Confirmed", it.getOrThrow()) }
        } finally { PatchRelayFunctions.installDispatcher(null) }
    }

    @Test fun delayedCompletionFailureAndDuplicateCallbacks() {
        try {
            lateinit var finish: (Result<String>) -> Unit
            var calls = 0
            var result: Result<String>? = null
            PatchRelayFunctions.installDispatcher { _, _, _, done -> finish = done }
            launch({ checkoutStatus(false) }) { result = it; calls++ }
            assertNull(result)
            finish(Result.failure(IllegalStateException("failed")))
            finish(Result.success("\"late\""))
            assertEquals("failed", result!!.exceptionOrNull()!!.message)
            assertEquals(1, calls)
            PatchRelayFunctions.installDispatcher { _, _, _, done -> done(Result.success("42")) }
            launch({ checkoutStatus(false) }) { assertTrue(it.isFailure) }
        } finally { PatchRelayFunctions.installDispatcher(null) }
    }
}
