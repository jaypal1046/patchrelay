package dev.patchrelay.demo.patch;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RecoveryPatchTest {
    @Test public void returnsTheWorkingResult() {
        String result = new RecoveryPatch().invoke("greet", "hello");
        assertTrue(result.contains("Characters: 5"));
        assertTrue(result.contains("Reversed: olleh"));
    }

    @Test public void rejectsUnsupportedOperation() {
        try { new RecoveryPatch().invoke("writeFile", "hello"); fail("Accepted unsupported operation"); }
        catch (IllegalArgumentException expected) { }
    }
}
