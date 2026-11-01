import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun OffersPage() {
    Card("offers") {
        Text("offers_title", "Offers v2", "title")
        Text("offers_delivery", "Free delivery on every order")
        Text("offers_points", "Double reward points on every notebook")
    }
}
