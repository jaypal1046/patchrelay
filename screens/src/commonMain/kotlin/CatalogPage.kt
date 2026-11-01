import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun CatalogPage(quantity: Int, add: () -> Unit) {
    Card("catalog") {
        Text("catalog_title", "Catalog v2", "title")
        Text("product_name", "Travel notebook")
        Text("product_price", "Unit price: $${productPrice()}")
        Text("catalog_count", "Items in cart: $quantity")
        Button("add_product", "Add travel notebook", quantity < 100, add)
    }
}
