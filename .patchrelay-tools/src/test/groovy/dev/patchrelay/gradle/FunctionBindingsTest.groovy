package dev.patchrelay.gradle
import org.junit.Test
import static org.junit.Assert.*
import org.gradle.api.GradleException

class FunctionBindingsTest {
    @Test void composeOptInRejectsUnsupportedBackendAndAsyncEffects() {
        assertTrue(FunctionBindings.discover(['App.kt':'import androidx.compose.runtime.Composable\n@Composable fun Screen(): Unit {}'], true).functions.empty)
        String app = '\n@PatchFunction fun total(): Int = 1'
        assertEquals(1, FunctionBindings.discover(['App.kt':'import androidx.compose.runtime.Composable\n@Composable fun Screen(): Unit {}' + app], true).functions.size())
        for (source in ['import androidx.compose.material.Text', 'import androidx.compose.runtime.LaunchedEffect as Launch', 'fun screen(): Unit { LaunchedEffect(Unit) {} }']) {
            try { FunctionBindings.discover(['App.kt':source + app], true); fail('Accepted unsupported Compose code') }
            catch (GradleException expected) { assertTrue(expected.message.startsWith('PatchRelay')) }
        }
    }
    private List inspect(String source) { FunctionBindings.inspect(['App.kt':source]) }
    @Test void stableAcrossBodyAndFileChanges() {
        def first = inspect('package shop; @PatchFunction fun total(a: Int, b: Int): Int = a + b')
        def moved = FunctionBindings.inspect(['Elsewhere.kt':'package shop; // moved\n @PatchFunction fun total(a: Int, b: Int): Int { return a * b }'])
        assertEquals(first, moved)
        assertEquals('shop.total.v1', first[0].id)
        assertEquals(first, inspect('package shop\r\n@PatchFunction\r\nfun total(a: Int, b: Int): Int = a+b'))
    }
    @Test void rejectsUnsupportedDeclarationsAndDuplicates() {
        [
            '@PatchFunction fun total(a: List<Int>, b: Int): Int = b',
            '@PatchFunction fun total(a: Long, b: Int): Int = b',
            '@PatchFunction fun total(a: Int, b: Int) = a+b',
            '@PatchFunction inline suspend fun total(a: Int, b: Int): Int = a+b',
            '@Composable fun Screen(): Unit {}; @PatchFunction fun total(): Int = 1',
            'fun patchRelayIntercept_total(): Int = 1; @PatchFunction fun total(): Int = 1',
            'class App { @PatchFunction fun total(a: Int, b: Int): Int = a+b }',
            '@PatchFunction fun Int.total(a: Int, b: Int): Int = a+b',
            '@PatchFunction fun <T> total(a: Int, b: Int): Int = a+b',
            '@PatchFunction("same") fun a(x: Int, y: Int): Int = x+y; @PatchFunction("same") fun b(x: Int, y: Int): Int = x-y'
        ].each { source ->
            try { inspect(source); fail('Accepted unsupported declaration: ' + source) }
            catch (GradleException expected) { assertTrue(expected.message.startsWith('PatchRelay')) }
        }
    }
    @Test void acceptsBoundedScalarAndSuspendSignatures() {
        assertEquals('suspend (String,Boolean,Int?,Double)->String?', inspect('@PatchFunction suspend fun greet(a: String, b: Boolean, c: Int?, d: Double): String? = a')[0].signature)
        assertEquals('()->Unit', inspect('@PatchFunction fun clear(): Unit {}')[0].signature)
    }
    @Test void discoversExplicitStateAndRejectsUnsupportedProperties() {
        def program = FunctionBindings.discover(['App.kt':'@PatchState("cart.quantity") internal var quantity: Int = 1; @PatchFunction fun total(): Int = quantity'])
        assertEquals('cart.quantity', program.states[0].id)
        assertEquals('Int', program.states[0].type)
        for (state in ['@PatchState val quantity: Int = 1', '@PatchState var quantity = 1', '@PatchState private var quantity: Int = 1']) {
            try { FunctionBindings.discover(['App.kt':state + '; @PatchFunction fun total(): Int = 1']); fail('Accepted bad state') }
            catch (GradleException expected) { assertTrue(expected.message.contains('@PatchState')) }
        }
    }
    @Test void generatesBothDispatchAndFallbackFromOneBody() {
        File out = java.nio.file.Files.createTempDirectory('patchrelay-generation').toFile()
        try {
            FunctionBindings.generate(inspect('@PatchFunction fun total(a: Int, b: Int): Int = a+b'), out)
            assertTrue(new File(out,'js/PatchRelayExports.kt').text.contains('fun invokeFunction('))
            assertTrue(new File(out,'js/PatchRelayExports.kt').text.contains('api.invokeFunction = ::invokeFunction'))
            assertTrue(new File(out,'common/PatchRelayFunctions.kt').text.contains('patchRelayNative0(arg0, arg1)'))
            assertTrue(new File(out,'functions.json').text.contains('(Int,Int)->Int'))
        } finally { out.deleteDir() }
    }
}
