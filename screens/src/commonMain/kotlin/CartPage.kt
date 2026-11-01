import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun CartPage(quantity: Int, add: () -> Unit, remove: () -> Unit) {
    Card("cart") {
        Text("cart_title", "Cart v2", "title")
        Text("cart_count", "Items: $quantity")
        Text("cart_total", "Total: $${checkoutTotal(quantity, productPrice())}")
        Text("cart_points", "Reward points: ${rewardPoints(quantity)}")
        Text("cart_delivery", "Free delivery included", "caption")
        Row("cart_actions") {
            Button("cart_add", "Add one", quantity < 100, add)
            Button("cart_remove", "Remove one", quantity > 0, remove)
        }
    }
}
