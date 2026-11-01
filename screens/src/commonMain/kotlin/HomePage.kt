import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun HomePage(quantity: Int, browse: () -> Unit) {
    Card("home") {
        Text("home_title", "Home v2", "title")
        Text("home_message", "Welcome back to the updated app")
        Text("home_count", "Items in cart: $quantity")
        Button("browse", "Explore catalog", onClick = browse)
        Text("home_update", "Every page updated with signed JavaScript", "caption")
    }
}
