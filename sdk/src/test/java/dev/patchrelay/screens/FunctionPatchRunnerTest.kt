package dev.patchrelay.screens

import PatchRelayFunctions
import checkoutStatus
import kotlin.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FunctionPatchRunnerTest {
    @Test fun screenOnlyProgramHasNoFunctionHooks() {
        FunctionPatchRunner(emptyMap(), "{}", { _, _, _ -> error("No functions") })
        try { FunctionPatchRunner(emptyMap(), "{\"unexpected.v1\":\"()->Int\"}", { _, _, _ -> }); fail("Accepted an uninstalled hook") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun ordinarySuspendCallUsesInstalledSdkRunner() {
        var result: Result<String>? = null
        val runner = FunctionPatchRunner(PatchRelayFunctions.contracts, "{\"checkout.status.v1\":\"suspend (Boolean)->String\"}", { _, _, done -> done("\"Downloaded\"") })
        try {
            PatchRelayFunctions.installDispatcher(runner::callSuspending)
            val call: suspend () -> String = { checkoutStatus(false) }
            call.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
            assertEquals("Downloaded", result!!.getOrThrow())
        } finally { PatchRelayFunctions.installDispatcher(null) }
    }
    @Test fun closingRunnerResumesPendingSuspendCallsOnce() {
        lateinit var late: (String) -> Unit
        var calls = 0
        val runner = FunctionPatchRunner(mapOf("later.v1" to "suspend ()->Int"), "{\"later.v1\":\"suspend ()->Int\"}", { _, _, done -> late = done })
        runner.callSuspending("later.v1", "[]", { error("No fallback") }) {
            assertEquals("closed", it.exceptionOrNull()?.message); calls++
        }
        assertEquals(0, calls)
        runner.call("later.v1", "[]", { error("No fallback") }) { fail("Closed legacy hook delivered") }
        runner.close(IllegalStateException("closed"))
        late("7")
        assertEquals(1, calls)
        runner.callSuspending("later.v1", "[]", { error("No fallback") }) { assertTrue(it.isFailure); calls++ }
        assertEquals(2, calls)
    }
    @Test fun generatedHooksRouteFallbackAndPatchedCalls() {
        var result = 0
        val native = FunctionPatchRunner(PatchRelayFunctions.contracts, "{}", { _, _, _ -> error("Unexpected JS") })
        PatchRelayFunctions.checkoutTotal(3, 100, native::call) { result = it }
        assertEquals(300, result)
        val patched = FunctionPatchRunner(PatchRelayFunctions.contracts, "{\"checkout.total.v1\":\"(Int,Int)->Int\"}", { id, args, callback ->
            assertEquals("checkout.total.v1", id); assertEquals("[3,100]", args); callback("293")
        })
        PatchRelayFunctions.checkoutTotal(3, 100, patched::call) { result = it }
        assertEquals(293, result)
        PatchRelayFunctions.checkoutShipping(10, 5, patched::call) { result = it }
        assertEquals(15, result)
    }

    @Test fun validatesScalarContractsArgumentsAndResults() {
        val contracts = mapOf("greeting.v1" to "(String,Boolean,Int?,Double)->String?")
        val bindings = "{\"greeting.v1\":\"(String,Boolean,Int?,Double)->String?\"}"
        var returned = ""
        val runner = FunctionPatchRunner(contracts, bindings, { _, _, done -> done("\"hello\"") })
        runner.call("greeting.v1", "[\"guest\",true,null,1.5]", { error("No fallback") }) { returned = it }
        assertEquals("\"hello\"", returned)
        for (args in listOf("[]", "[1,true,null,1.5]", "[\"guest\",1,null,1.5]", "[\"guest\",true,2147483648,1.5]")) {
            try { runner.call("greeting.v1", args, { error("No fallback") }) {}; fail("Accepted $args") }
            catch (_: IllegalArgumentException) {}
        }
        for (result in listOf("{}", "[]", "42", "\"" + "x".repeat(4097) + "\"", "[".repeat(33))) {
            val bad = FunctionPatchRunner(contracts, bindings, { _, _, done -> done(result) })
            try { bad.call("greeting.v1", "[\"guest\",true,null,1.5]", { error("No fallback") }) {}; fail("Accepted invalid result") }
            catch (_: Exception) {}
        }
        for (binding in listOf("{\"unknown.v1\":\"()->Unit\"}", "{\"greeting.v1\":\"()->Int\"}")) {
            try { FunctionPatchRunner(contracts, binding, { _, _, _ -> }); fail("Accepted incompatible binding") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun suspendedFallbackCompletesOnceAndReportsFailure() {
        var resume: ((Result<String>) -> Unit)? = null
        var calls = 0
        val runner = FunctionPatchRunner(mapOf("later.v1" to "suspend ()->Int"), "{}", { _, _, _ -> error("No JS") })
        runner.call("later.v1", "[]", { resume = it }) { assertEquals("7", it); calls++ }
        assertEquals(0, calls)
        resume!!(Result.success("7")); resume!!(Result.success("8"))
        assertEquals(1, calls)
        var failure: Exception? = null
        val failing = FunctionPatchRunner(mapOf("later.v1" to "suspend ()->Int"), "{}", { _, _, _ -> }, { failure = it })
        failing.call("later.v1", "[]", { it(Result.failure(IllegalStateException("failed"))) }) { fail("Unexpected result") }
        assertEquals("failed", failure?.message)
    }
}
