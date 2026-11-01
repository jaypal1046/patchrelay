package dev.patchrelay.screens

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenStateStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val schema = "a".repeat(64)
    private val snapshot = "{\"schema\":\"$schema\",\"values\":{\"quantity\":2,\"email\":\"a@example.com\"}}"
    @Test fun persistsAcrossInstancesAndIsolatesNativeUpgrades() {
        val base = "state-test-" + java.util.UUID.randomUUID()
        val store = ScreenStateStore(context, base, "b".repeat(64))
        assertNull(store.load()); store.save(snapshot)
        assertEquals(snapshot, ScreenStateStore(context, base, "b".repeat(64)).load())
        assertNull(ScreenStateStore(context, base, "c".repeat(64)).load())
        assertNull(ScreenStateStore(context, "$base-new", "b".repeat(64)).load())
        assertEquals(snapshot, store.load())
    }
    @Test fun rejectsInvalidStateWithoutOverwritingLastGoodSnapshot() {
        val store = ScreenStateStore(context, java.util.UUID.randomUUID().toString(), "b".repeat(64))
        store.save(snapshot)
        for (bad in listOf("{}", snapshot.replace("2,", "{},"), snapshot + " ".repeat(8193))) {
            try { store.save(bad); fail("Accepted invalid state") }
            catch (_: Exception) {}
            assertEquals(snapshot, store.load())
        }
    }
}
