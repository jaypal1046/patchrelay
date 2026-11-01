import androidx.compose.runtime.*
import dev.patchrelay.compose.*

private val app by lazy {
    if (savedPage !in setOf("home", "catalog", "cart", "profile", "offers")) savedPage = "home"
    RelayComposition("checkout", backAction = { if (savedPage == "home") null else "simple_back" }) { SimpleApp() }
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
            "home" -> HomePage(quantity) { page = "catalog"; savedPage = page }
            "catalog" -> CatalogPage(quantity) { if (quantity < 100) { quantity++; savedQuantity = quantity } }
            "cart" -> CartPage(quantity, { if (quantity < 100) { quantity++; savedQuantity = quantity } }, { if (quantity > 0) { quantity--; savedQuantity = quantity } })
            "profile" -> ProfilePage(name) { name = it.take(40); savedName = name }
            "offers" -> OffersPage()
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
