package dev.patchrelay.demo;

import android.content.Context;
import android.test.InstrumentationTestCase;
import dev.patchrelay.Patch;
import dev.patchrelay.PatchRelay;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public final class PatchRelayTest extends InstrumentationTestCase {
    private Context app;
    private File directory;

    @Override protected void setUp() throws Exception {
        super.setUp();
        app = getInstrumentation().getTargetContext();
        app.getSharedPreferences("patchrelay", 0).edit().clear().commit();
        directory = new File(new File(app.getNoBackupFilesDir(), "patchrelay"),
                UUID.nameUUIDFromBytes("1".getBytes(StandardCharsets.UTF_8)).toString());
        directory.mkdirs();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) assertTrue(file.delete());
    }

    private PatchRelay relay() throws Exception { return new PatchRelay(app, "1", BuildConfig.PATCH_PUBLIC_KEY); }
    private Patch fallback() { return (operation, input) -> "original"; }

    private void stageFixture() throws Exception {
        relay();
        for (String name : new String[]{"1.dex", "1.json"}) {
            File file = new File(directory, name);
            try (InputStream in = getInstrumentation().getContext().getAssets().open(name);
                 FileOutputStream out = new FileOutputStream(file)) {
                assertTrue(file.setReadOnly());
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
        }
        assertTrue(app.getSharedPreferences("patchrelay", 0).edit().putLong("pending", 1).putLong("highest", 1).commit());
    }

    public void testLoadsDexInNonDebuggableApp() throws Exception {
        assertEquals(0, app.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE);
        stageFixture();
        PatchRelay relay = relay();
        assertEquals("Patched Java logic: hello test", relay.start(fallback()).invoke("greet", "test"));
        assertEquals(1L, relay.activeVersion());
        relay.markHealthy();
        PatchRelay nextLaunch = relay();
        assertEquals("Patched Java logic: hello restart", nextLaunch.start(fallback()).invoke("greet", "restart"));
        nextLaunch.markHealthy();
    }

    public void testUnconfirmedLaunchRollsBack() throws Exception {
        stageFixture();
        relay().start(fallback());
        PatchRelay nextLaunch = relay();
        assertEquals("original", nextLaunch.start(fallback()).invoke("greet", "test"));
        assertEquals(0L, nextLaunch.activeVersion());
        assertEquals(1L, app.getSharedPreferences("patchrelay", 0).getLong("highest", 0));
    }

    public void testTamperedDexFallsBack() throws Exception {
        stageFixture();
        File dex = new File(directory, "1.dex");
        assertTrue(dex.delete());
        try (FileOutputStream out = new FileOutputStream(dex)) {
            assertTrue(dex.setReadOnly());
            out.write(new byte[]{1, 2, 3});
        }
        PatchRelay relay = relay();
        assertEquals("original", relay.start(fallback()).invoke("greet", "test"));
        assertEquals(0L, relay.activeVersion());
    }

    public void testFailingRestoredPatchEventuallyUsesBuiltIn() throws Exception {
        stageFixture();
        assertTrue(app.getSharedPreferences("patchrelay", 0).edit().remove("pending")
                .putLong("active", 2).putLong("previous", 1).putBoolean("booting", true).commit());
        PatchRelay restored = relay();
        assertEquals("Patched Java logic: hello restored", restored.start(fallback()).invoke("greet", "restored"));
        // Simulate another process death before confirmation of the restored patch.
        PatchRelay nextLaunch = relay();
        assertEquals("original", nextLaunch.start(fallback()).invoke("greet", "test"));
        assertEquals(0L, nextLaunch.activeVersion());
    }

    public void testTamperedManifestFallsBack() throws Exception {
        stageFixture();
        File manifest = new File(directory, "1.json");
        assertTrue(manifest.delete());
        Files.write(manifest.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        PatchRelay relay = relay();
        assertEquals("original", relay.start(fallback()).invoke("greet", "test"));
    }

    public void testNewBaseDoesNotLoadOldPatch() throws Exception {
        stageFixture();
        PatchRelay upgraded = new PatchRelay(app, "2", BuildConfig.PATCH_PUBLIC_KEY);
        assertEquals("original", upgraded.start(fallback()).invoke("greet", "test"));
    }

    public void testRejectsCleartextAndMainThread() throws Exception {
        try { relay().stage("http://example.com/update.json"); fail("Accepted HTTP"); }
        catch (IOException expected) { assertEquals("HTTPS URL required", expected.getMessage()); }
        final Throwable[] failure = new Throwable[1];
        getInstrumentation().runOnMainSync(() -> {
            try { relay().start(fallback()); } catch (Throwable error) { failure[0] = error; }
        });
        assertTrue(failure[0] instanceof IllegalStateException);
    }
}
