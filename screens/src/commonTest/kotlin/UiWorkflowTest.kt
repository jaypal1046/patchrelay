import kotlin.test.*

class UiWorkflowTest {
    @Test fun pagesCallbacksPricingAndSavedStateWorkTogether() {
        assertTrue(renderAppScreen("checkout").contains("Home v2"))
        assertTrue(handleAppEvent("checkout", "c_browse").contains("Catalog v2"))
        assertTrue(handleAppEvent("checkout", "c_add_product").contains("Items in cart: 1"))
        val cart = handleAppEvent("checkout", "c_nav_cart")
        assertTrue(cart.contains("Total: $9"))
        assertTrue(cart.contains("Reward points: 20"))
        assertTrue(handleAppEvent("checkout", "c_cart_add").contains("Items: 2"))
        handleAppEvent("checkout", "c_nav_profile")
        assertTrue(handleAppEvent("checkout", "c_profile_name", "Jay").contains("Hello Jay"))
        assertEquals(2, savedQuantity)
        assertEquals("Jay", savedName)
        assertTrue(handleAppEvent("checkout", "c_nav_offers").contains("Offers v2"))
        assertTrue(handleAppEvent("checkout", "simple_back").contains("Home v2"))
        assertFailsWith<IllegalArgumentException> { renderAppScreen("other") }
    }
}
