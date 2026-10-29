package dev.patchrelay.demo.patch;

import org.junit.Test;
import static org.junit.Assert.*;

public final class R8PatchTest {
    @Test public void formatsKnownInput() {
        assertTrue(new R8Patch().invoke("greet", "hello").contains("Output: OLLEH"));
    }

    @Test public void rejectsUnsupportedOperation() {
        try { new R8Patch().invoke("writeFile", "hello"); fail("Accepted unsupported operation"); }
        catch (IllegalArgumentException expected) { }
    }
}
