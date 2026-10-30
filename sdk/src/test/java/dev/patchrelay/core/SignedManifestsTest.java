package dev.patchrelay.core;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import static org.junit.Assert.*;

public class SignedManifestsTest {
    @Test public void verifiesNativeContractAndRejectsIncompatibleMetadata() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        JSONObject metadata = new JSONObject().put("schema", 1).put("apiVersion", 1)
            .put("appId", "example.app").put("baseVersion", "native-base1").put("version", 1);
        assertEquals(1, SignedManifests.verify(sign(metadata, keys), publicKey, "example.app", "native-base1").getLong("version"));
        rejected(sign(metadata, keys), publicKey, "other.app", "native-base1");
        rejected(sign(metadata, keys), publicKey, "example.app", "wrong-base");
        metadata.put("schema", 2);
        rejected(sign(metadata, keys), publicKey, "example.app", "native-base1");
        metadata.put("schema", 1).put("apiVersion", 2);
        rejected(sign(metadata, keys), publicKey, "example.app", "native-base1");
        metadata.put("apiVersion", 1).put("version", 0);
        rejected(sign(metadata, keys), publicKey, "example.app", "native-base1");
        metadata.put("version", 1);
        JSONObject envelope = new JSONObject(new String(sign(metadata, keys), StandardCharsets.UTF_8));
        envelope.put("payload", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
        rejected(envelope.toString().getBytes(StandardCharsets.UTF_8), publicKey, "example.app", "native-base1");
    }

    private static byte[] sign(JSONObject metadata, KeyPair keys) throws Exception {
        byte[] payload = metadata.toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate()); signer.update(payload);
        return new JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void rejected(byte[] bytes, String key, String app, String base) throws Exception {
        try { SignedManifests.verify(bytes, key, app, base); fail("Accepted incompatible or altered manifest"); }
        catch (SecurityException expected) { }
    }

    @Test public void rejectsNonHttpsBeforeOpeningConnection() {
        try { HttpsDownloads.download("http://example.com/patch", 100); fail("Accepted HTTP"); }
        catch (java.io.IOException expected) { }
    }
}
