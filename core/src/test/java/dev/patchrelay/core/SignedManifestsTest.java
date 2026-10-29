package dev.patchrelay.core;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import static org.junit.Assert.*;

public class SignedManifestsTest {
    @Test public void isolatesRuntimesAndPreservesLegacyDexContract() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        JSONObject metadata = new JSONObject().put("schema", 2).put("apiVersion", 1)
            .put("appId", "example.app").put("baseVersion", "rn-0.82.1-base1").put("version", 1)
            .put("runtime", "react-native-android").put("artifactType", "js-bundle");
        assertEquals(1, SignedManifests.verify(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", "react-native-android").getLong("version"));
        rejected(sign(metadata, keys), publicKey, "other.app", "rn-0.82.1-base1", "react-native-android");
        rejected(sign(metadata, keys), publicKey, "example.app", "wrong-base", "react-native-android");
        rejected(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", null);
        metadata.put("runtime", "flutter");
        rejected(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", "react-native-android");
        metadata.put("runtime", "react-native-android").put("artifactType", "dex");
        rejected(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", "react-native-android");
        metadata.put("schema", 1).put("artifactType", "js-bundle");
        SignedManifests.verify(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1");
        rejected(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", "react-native-android");
        metadata.put("version", 0);
        rejected(sign(metadata, keys), publicKey, "example.app", "rn-0.82.1-base1", null);
        JSONObject envelope = new JSONObject(new String(sign(metadata, keys), StandardCharsets.UTF_8));
        envelope.put("payload", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
        rejected(envelope.toString().getBytes(StandardCharsets.UTF_8), publicKey, "example.app", "rn-0.82.1-base1", null);
    }

    private static byte[] sign(JSONObject metadata, KeyPair keys) throws Exception {
        byte[] payload = metadata.toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate()); signer.update(payload);
        return new JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void rejected(byte[] bytes, String key, String app, String base, String runtime) throws Exception {
        try { SignedManifests.verify(bytes, key, app, base, runtime); fail("Accepted incompatible or altered manifest"); }
        catch (SecurityException expected) { }
    }

    @Test public void rejectsNonHttpsBeforeOpeningConnection() {
        try { HttpsDownloads.download("http://example.com/patch", 100); fail("Accepted HTTP"); }
        catch (java.io.IOException expected) { }
    }
}
