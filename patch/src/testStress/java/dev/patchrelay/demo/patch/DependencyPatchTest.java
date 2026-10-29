package dev.patchrelay.demo.patch;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DependencyPatchTest {
    @Test public void computesKnownHashThroughNewLibrary() {
        assertEquals("Patch 2: new dependency\nApache Commons Codec\nInput: hello\nSHA-256: 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                new DependencyPatch().invoke("greet", "hello"));
    }

    @Test public void rejectsUnsupportedOperation() {
        try { new DependencyPatch().invoke("writeFile", "hello"); fail("Accepted unsupported operation"); }
        catch (IllegalArgumentException expected) { }
    }
}
