import androidx.compose.runtime.*
import dev.patchrelay.compose.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ComposeScreenTest {
    @Test fun inputToggleAndDisposalUseComposeLifecycle() {
        var disposed = 0
        val screen = RelayComposition("test") {
            var name by remember { mutableStateOf("") }
            var checked by remember { mutableStateOf(false) }
            DisposableEffect(Unit) { onDispose { disposed++ } }
            Column("panel") {
                TextField("name", "Name", name) { name = it }
                Toggle("enabled", "Enabled", checked) { checked = it }
                Text("status", "$name:$checked")
            }
        }
        screen.render()
        assertTrue(screen.event("c_name", "Ada").contains("Ada:false"))
        assertTrue(screen.event("c_enabled", "true").contains("Ada:true"))
        screen.dispose(); screen.dispose()
        assertEquals(1, disposed)
    }
    @Test fun realRememberStateCallbacksAndNavigation() {
        val screen = RelayComposition("test", backAction = { "back" }) {
            var count by remember { mutableStateOf(0) }
            var detail by remember { mutableStateOf(false) }
            Column("panel") {
                Text("count", "Count $count")
                Button("add", "Add") { count++ }
                Button("nav", "Navigate") { detail = !detail }
                if (detail) Text("detail", "Detail $count")
            }
        }
        try {
            assertTrue(screen.render().contains("Count 0"))
            assertTrue(screen.event("c_add").contains("Count 1"))
            assertTrue(screen.event("c_nav").contains("Detail 1"))
            assertTrue(screen.event("c_add").contains("Detail 2"))
            assertFalse(screen.event("c_nav").contains("Detail 2"))
            assertEquals("back", Json.parseToJsonElement(screen.render()).jsonObject["backAction"]!!.jsonPrimitive.content)
        } finally { screen.dispose() }
        assertFailsWith<IllegalStateException> { screen.render() }
    }

    @Test fun keyedReorderingUsesApplierMovesAndRememberedIdentity() {
        val screen = RelayComposition("test") {
            var reversed by remember { mutableStateOf(false) }
            Column("panel") {
                Button("reverse", "Reverse") { reversed = !reversed }
                for (id in if (reversed) listOf("b", "a") else listOf("a", "b")) key(id) {
                    val remembered = remember { id }
                    Text(id, remembered)
                }
            }
        }
        try {
            screen.render()
            val reversed = screen.event("c_reverse")
            assertTrue(reversed.indexOf("\"id\":\"b\"") < reversed.indexOf("\"id\":\"a\""))
            val original = screen.event("c_reverse")
            assertTrue(original.indexOf("\"id\":\"a\"") < original.indexOf("\"id\":\"b\""))
        } finally { screen.dispose() }
    }

    @Test fun invalidTreesDisabledActionsAndMalformedEventsAreRejected() {
        val duplicate = RelayComposition("test") { Text("same", "A"); Text("same", "B") }
        assertFailsWith<IllegalArgumentException> { duplicate.render() }
        val disabled = RelayComposition("test") { Button("disabled", "Disabled", false) { error("Must not run") } }
        disabled.render()
        assertFailsWith<IllegalArgumentException> { disabled.event("c_disabled") }
        val toggle = RelayComposition("test") { Toggle("toggle", "Toggle", false) {} }
        toggle.render()
        assertFailsWith<IllegalArgumentException> { toggle.event("c_toggle", "invalid") }
    }
}
