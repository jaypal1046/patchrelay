package dev.patchrelay.demo.patch;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RollbackPatchTest {
    @Test public void failsWithTheControlledErrorBeforeStartupConfirmation() {
        try { new RollbackPatch().invoke("greet", "PatchRelay"); fail("Expected controlled failure"); }
        catch (IllegalStateException expected) {
            assertEquals("Patch 5: repeated controlled startup failure.\nRollback test: restart once more to restore patch 3.", expected.getMessage());
        }
    }
}
