import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun Navigation(page: String, navigate: (String) -> Unit) {
    Row("navigation") {
        Button("nav_home", "Start", page != "home") { navigate("home") }
        Button("nav_catalog", "Shop", page != "catalog") { navigate("catalog") }
        Button("nav_cart", "Basket", page != "cart") { navigate("cart") }
        Button("nav_profile", "Account", page != "profile") { navigate("profile") }
        Button("nav_offers", "Offers", page != "offers") { navigate("offers") }
    }
}
