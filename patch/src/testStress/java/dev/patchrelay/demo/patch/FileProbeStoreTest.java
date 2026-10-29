package dev.patchrelay.demo.patch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public final class FileProbeStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void changesActualFileAndKeepsItsContentsOnRepeat() throws Exception {
        File root = temporary.newFolder();
        String first = FileProbeStore.INSTANCE.update(root);
        assertTrue(first.contains("Before: {\"patchrelayTest\":4,\"revision\":1}"));
        File file = new File(root, FileProbeStore.FILE_NAME);
        assertEquals("{\"patchrelayTest\":4,\"revision\":2}", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertTrue(FileProbeStore.INSTANCE.update(root).contains("Before: {\"patchrelayTest\":4,\"revision\":2}"));
    }

    @Test public void preservesUnrecognizedExistingContents() throws Exception {
        File root = temporary.newFolder();
        File file = new File(root, FileProbeStore.FILE_NAME);
        Files.write(file.toPath(), "existing unrelated data".getBytes(StandardCharsets.UTF_8));
        try { FileProbeStore.INSTANCE.update(root); fail("Overwrote unrelated data"); }
        catch (IllegalStateException expected) { }
        assertEquals("existing unrelated data", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }
}
