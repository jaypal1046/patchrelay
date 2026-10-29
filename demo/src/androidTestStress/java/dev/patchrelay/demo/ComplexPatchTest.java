package dev.patchrelay.demo;

import android.content.Context;
import android.test.InstrumentationTestCase;
import dev.patchrelay.Patch;
import dev.patchrelay.PatchRelay;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Run only against the isolated R8-minified stress build. */
public final class ComplexPatchTest extends InstrumentationTestCase {
    private Context app;
    private File directory;

    @Override protected void setUp() throws Exception {
        super.setUp();
        app = getInstrumentation().getTargetContext();
        assertEquals("dev.patchrelay.demo.stress", app.getPackageName());
        assertEquals(0, app.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE);
        app.getSharedPreferences("patchrelay", 0).edit().clear().commit();
        directory = new File(new File(app.getNoBackupFilesDir(), "patchrelay"),
                UUID.nameUUIDFromBytes("stress-1".getBytes(StandardCharsets.UTF_8)).toString());
        directory.mkdirs();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) PatchRelayTest.deleteTree(file);
    }

    private PatchRelay relay() throws Exception { return new PatchRelay(app, "stress-1", BuildConfig.PATCH_PUBLIC_KEY); }

    private PatchRelay fixture(String variant) throws Exception {
        PatchRelay relay = relay();
        for (String extension : new String[]{"dex", "json"}) {
            File file = new File(directory, "1." + extension);
            try (InputStream in = getInstrumentation().getContext().getAssets().open("stress/" + variant + "." + extension);
                 FileOutputStream out = new FileOutputStream(file)) {
                assertTrue(file.setReadOnly());
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
        }
        assertTrue(app.getSharedPreferences("patchrelay", 0).edit().putLong("pending", 1).putLong("highest", 1).commit());
        return relay;
    }

    public void testLoadsPatchInR8ReleaseWithSdkKeepRules() throws Exception {
        PatchRelay relay = fixture("bundled");
        assertEquals("Complex Kotlin patch: hello R8", relay.start((op, input) -> "original").invoke("greet", "R8"));
        relay.markHealthy();
        assertEquals(1L, relay.activeVersion());
    }

    public void testR8ProcessedPatchKeepsReflectiveEntryPoint() throws Exception {
        PatchRelay relay = fixture("minified");
        assertEquals("Complex Kotlin patch: hello minified", relay.start((op, input) -> "original").invoke("greet", "minified"));
        relay.markHealthy();
    }

    public void testNewDependencyMissingFromThinPatchFailsWhenCalled() throws Exception {
        Patch logic = fixture("thin").start((op, input) -> "original");
        try { logic.invoke("newDependency", "hello"); fail("Thin patch unexpectedly contains commons-codec"); }
        catch (NoClassDefFoundError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("org/apache/commons/codec")
                    || expected.getMessage().contains("org.apache.commons.codec"));
        }
    }

    public void testBundledNewDependencyWorks() throws Exception {
        PatchRelay relay = fixture("bundled");
        Patch logic = relay.start((op, input) -> "original");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", logic.invoke("newDependency", "hello"));
        relay.markHealthy();
    }

    public void testBundledUpgradeDoesNotReplaceHostDependency() throws Exception {
        Patch logic = fixture("bundled").start((op, input) -> "original");
        assertEquals("2.8.9", logic.invoke("dependencyVersion", ""));
        try { logic.invoke("newDependencyApi", "hello"); fail("Unexpectedly upgraded host Gson"); }
        catch (NoSuchMethodError expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("disableJdkUnsafe")); }
    }

    public void testDexDoesNotReplaceApkAssetOrCarryJarResource() throws Exception {
        String before;
        try (InputStream in = app.getAssets().open("catalog.json")) {
            before = new String(readAll(in), StandardCharsets.UTF_8);
        }
        assertTrue(before.contains("Original APK asset"));
        PatchRelay relay = fixture("bundled");
        Patch logic = relay.start((op, input) -> "original");
        assertEquals("asset-not-present-in-dex", logic.invoke("readPatchAsset", ""));
        try (InputStream in = app.getAssets().open("catalog.json")) {
            assertEquals(before, new String(readAll(in), StandardCharsets.UTF_8));
        }
        relay.markHealthy();
    }

    public void testPrivateFileChangeSurvivesCodeRollback() throws Exception {
        File data = new File(app.getFilesDir(), "patchrelay-stress-data.json");
        try {
            Files.write(data.toPath(), "{\"revision\":1}".getBytes(StandardCharsets.UTF_8));
            PatchRelay relay = fixture("bundled");
            assertEquals("written", relay.start((op, input) -> "original").invoke("writeFile", data.getAbsolutePath()));
            // No markHealthy: next startup must revert code, but cannot undo app data writes.
            PatchRelay next = relay();
            assertEquals("original", next.start((op, input) -> "original").invoke("greet", ""));
            assertEquals(0L, next.activeVersion());
            assertTrue(new String(Files.readAllBytes(data.toPath()), StandardCharsets.UTF_8).contains("\"revision\":2"));
        } finally { assertTrue(data.delete()); }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }
}
