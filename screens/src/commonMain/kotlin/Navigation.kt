import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun Navigation(page: String, navigate: (String) -> Unit) {
    Row("navigation") {
        Button("nav_home", "Start", page != "home1") { navigate("home1") }
        Button("nav_catalog", "Shop", page != "catalog1") { navigate("catalog1") }
        Button("nav_cart", "Basket", page != "cart1") { navigate("cart1") }
        Button("nav_profile", "Account", page != "profile1") { navigate("profile1") }
        Button("nav_offers", "Offers", page != "offers1") { navigate("offers1") }
    }
}
