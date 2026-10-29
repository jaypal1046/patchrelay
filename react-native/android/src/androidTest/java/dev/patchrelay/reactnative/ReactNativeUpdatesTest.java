package dev.patchrelay.reactnative;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.util.Base64;
import java.util.UUID;

public class ReactNativeUpdatesTest extends InstrumentationTestCase {
    private Context context;
    private String base, publicKey;
    private KeyPair keys;
    private SharedPreferences state;
    private File directory;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        base = UUID.randomUUID().toString();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        keys = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        directory = new File(context.getNoBackupFilesDir(), "patchrelay-react-native/" + UUID.nameUUIDFromBytes(base.getBytes(StandardCharsets.UTF_8)));
        new ReactNativeUpdates(context, base, publicKey);
        state = context.getSharedPreferences("patchrelay-react-native", Context.MODE_PRIVATE);
    }

    public void testConfirmedBundlePersistsAndReplayHighWaterRemains() throws Exception {
        fixture(1, "react-native-android");
        assertTrue(state.edit().putLong("highest", 1).putLong("pending", 1).commit());
        ReactNativeUpdates first = new ReactNativeUpdates(context, base, publicKey);
        assertNotNull(first.start()); assertEquals(1, first.activeVersion()); first.markHealthy();
        ReactNativeUpdates reopened = new ReactNativeUpdates(context, base, publicKey);
        assertNotNull(reopened.start()); assertEquals(1, reopened.activeVersion()); reopened.markHealthy();
        assertEquals(1, state.getLong("highest", 0));
    }

    public void testUnconfirmedBundleReturnsToPrevious() throws Exception {
        fixture(1, "react-native-android"); fixture(2, "react-native-android");
        assertTrue(state.edit().putLong("active", 1).putLong("pending", 2).putLong("highest", 2).commit());
        ReactNativeUpdates first = new ReactNativeUpdates(context, base, publicKey);
        first.start(); assertEquals(2, first.activeVersion());
        ReactNativeUpdates reopened = new ReactNativeUpdates(context, base, publicKey);
        reopened.start(); assertEquals(1, reopened.activeVersion()); reopened.markHealthy();
        assertEquals(2, state.getLong("highest", 0));
    }

    public void testTamperedBundleRestoresPrevious() throws Exception {
        fixture(1, "react-native-android"); fixture(2, "react-native-android");
        Files.write(new File(directory, "2.bundle").toPath(), "altered".getBytes(StandardCharsets.UTF_8));
        assertTrue(state.edit().putLong("active", 1).putLong("pending", 2).putLong("highest", 2).commit());
        ReactNativeUpdates updates = new ReactNativeUpdates(context, base, publicKey);
        assertNotNull(updates.start()); assertEquals(1, updates.activeVersion()); updates.markHealthy();
    }

    public void testWrongRuntimeFallsBackToApk() throws Exception {
        fixture(1, "flutter");
        assertTrue(state.edit().putLong("pending", 1).putLong("highest", 1).commit());
        ReactNativeUpdates updates = new ReactNativeUpdates(context, base, publicKey);
        assertNull(updates.start()); assertEquals(0, updates.activeVersion());
        assertEquals(1, state.getLong("highest", 0));
    }

    private void fixture(long version, String runtime) throws Exception {
        byte[] bundle = ("global.patchRelayVersion=" + version + ";").getBytes(StandardCharsets.UTF_8);
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bundle)) hash.append(String.format("%02x", value & 255));
        JSONObject metadata = new JSONObject().put("schema", 2).put("apiVersion", 1).put("appId", context.getPackageName())
            .put("baseVersion", base).put("version", version).put("runtime", runtime).put("artifactType", "js-bundle")
            .put("sha256", hash.toString()).put("artifactUrl", "https://example.com/index.bundle");
        byte[] payload = metadata.toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(keys.getPrivate()); signer.update(payload);
        byte[] envelope = new JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().getBytes(StandardCharsets.UTF_8);
        Files.write(new File(directory, version + ".bundle").toPath(), bundle);
        Files.write(new File(directory, version + ".json").toPath(), envelope);
    }
}
