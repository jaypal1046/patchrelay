import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UiTest {
    @Test fun escapesJsonStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\u0000\"", jsonString("a\"b\\c\n\r\t\u0000"))
        assertEquals("\"Guest\"", jsonString("Guest"))
    }
    @Test fun rejectsUnknownScreenAndEvent() {
        assertFailsWith<IllegalArgumentException> { renderScreen("unknown") }
        assertFailsWith<IllegalStateException> { handleEvent("checkout", "launch-native-code") }
    }
}
