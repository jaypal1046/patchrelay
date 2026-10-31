package dev.patchrelay.screens.demo

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.patchrelay.screens.ScreenEngine
import dev.patchrelay.screens.ScreenNode
import dev.patchrelay.screens.ScreenProgram
import dev.patchrelay.screens.ScreenTree
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SilentRecoveryTest {
    private fun await(condition: () -> Boolean) {
        for (attempt in 0 until 200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail("Host did not finish recovery")
    }
    private fun engine(activity: MainActivity): ScreenEngine? = MainActivity::class.java.getDeclaredField("engine")
        .apply { isAccessible = true }.get(activity) as ScreenEngine?
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> callback(engine: ScreenEngine, name: String): (T) -> Unit =
        ScreenEngine::class.java.getDeclaredField(name).apply { isAccessible = true }.get(engine) as (T) -> Unit

    @Test fun failedPatchRestoresBundledSelectionWithoutExposingDiagnostics() {
        val app = RuntimeEnvironment.getApplication() as DemoApp
        app.prepared.get(5, java.util.concurrent.TimeUnit.SECONDS)
        app.selected = ScreenProgram(2, "throw new Error('private failure')")
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            await { engine(activity) != null }
            // Inject the engine's failure callback; Robolectric does not execute WebView JS.
            callback<Exception>(engine(activity)!!, "onFailure")(IllegalStateException("private failure"))
            await { app.selected?.version == 0L && engine(activity) != null }
            val visible = texts(activity.findViewById(android.R.id.content)).joinToString("\n")
            assertTrue(visible.contains("Checkout"))
            for (forbidden in listOf("private failure", "Patch failed", "Recovery failed", "Screen unavailable")) {
                assertFalse("Exposed diagnostic: $forbidden", visible.contains(forbidden))
            }
        } finally { controller.destroy() }
    }

    @Test fun candidateScreenRemainsHiddenUntilPreparedFunctionsAndHealthSucceed() {
        val app = RuntimeEnvironment.getApplication() as DemoApp
        app.prepared.get(5, java.util.concurrent.TimeUnit.SECONDS)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            await { engine(activity) != null }
            callback<ScreenTree>(engine(activity)!!, "onTree")(
                ScreenTree("checkout", listOf(ScreenNode("candidate", "text", "UNCONFIRMED PATCH"))))
            val visible = texts(activity.findViewById(android.R.id.content)).joinToString("\n")
            assertFalse(visible.contains("UNCONFIRMED PATCH"))
            assertFalse(visible.contains("JS patch"))
        } finally { controller.destroy() }
    }
}
