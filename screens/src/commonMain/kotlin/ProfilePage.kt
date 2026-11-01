import androidx.compose.runtime.Composable
import dev.patchrelay.compose.*

@Composable
internal fun ProfilePage(name: String, updateName: (String) -> Unit) {
    Card("profile") {
        Text("profile_title", "Profile v2", "title")
        Text("profile_greeting", "Hello $name")
        TextField("profile_name", "Your name", name, onValueChange = updateName)
        Text("profile_message", "Your name survives restarts and compatible patches")
    }
}
