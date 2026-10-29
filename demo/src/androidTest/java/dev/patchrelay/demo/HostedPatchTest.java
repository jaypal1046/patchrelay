package dev.patchrelay.demo;

import android.test.InstrumentationTestCase;
import dev.patchrelay.PatchRelay;

/** Explicit network integration test against the signed public demo release. */
public final class HostedPatchTest extends InstrumentationTestCase {
    private static final String URL = "https://github.com/jaypal1046/patchrelay/releases/latest/download/patch.json";

    public void testStageVerifyAndActivateFromGithub() throws Exception {
        android.content.Context app = getInstrumentation().getTargetContext();
        app.getSharedPreferences("patchrelay", 0).edit().clear().commit();
        java.io.File root = new java.io.File(app.getNoBackupFilesDir(), "patchrelay");
        java.io.File[] builds = root.listFiles();
        if (builds != null) for (java.io.File build : builds) {
            java.io.File[] files = build.listFiles();
            if (files != null) for (java.io.File file : files) assertTrue(file.delete());
            assertTrue(build.delete());
        }
        PatchRelay relay = new PatchRelay(app, "1", BuildConfig.PATCH_PUBLIC_KEY);
        assertTrue(relay.stage(URL));
        assertFalse(relay.stage(URL));
        assertEquals("Patched Java logic: hello github", relay.start((op, input) -> "original").invoke("greet", "github"));
        relay.markHealthy();
        assertEquals(1L, relay.activeVersion());
        PatchRelay incompatible = new PatchRelay(app, "different-build", BuildConfig.PATCH_PUBLIC_KEY);
        try { incompatible.stage(URL); fail("Accepted incompatible build"); }
        catch (SecurityException expected) { }
    }
}
