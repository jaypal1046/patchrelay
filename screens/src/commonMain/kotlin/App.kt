import androidx.compose.runtime.*
import dev.patchrelay.compose.*

private val app by lazy {
    if (savedPage !in setOf("home1", "catalog1", "cart1", "profile1", "offers1")) savedPage = "home1"
    RelayComposition("checkout", backAction = { if (savedPage == "home1") null else "simple_back" }) { SimpleApp() }
}

@Composable
private fun SimpleApp() {
    var page by remember { mutableStateOf(savedPage) }
    var quantity by remember { mutableStateOf(savedQuantity) }
    var name by remember { mutableStateOf(savedName) }
    Column("simple_app") {
        Text("app_title", "Simple pages v2", "title")
        Navigation(page) { page = it; savedPage = it }
        when (page) {
            "home1" -> HomePage(quantity) { page = "catalog1"; savedPage = page }
            "catalog1" -> CatalogPage(quantity) { if (quantity < 100) { quantity++; savedQuantity = quantity } }
            "cart1" -> CartPage(quantity, { if (quantity < 100) { quantity++; savedQuantity = quantity } }, { if (quantity > 0) { quantity--; savedQuantity = quantity } })
            "profile1" -> ProfilePage(name) { name = it.take(40); savedName = name }
            "offers1" -> OffersPage()
            else -> error("Unknown page: $page")
        }
    }
}

fun renderAppScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    return app.render()
}

fun handleAppEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen" }
    return app.event(if (action == "simple_back") "c_nav_home" else action, value)
}
