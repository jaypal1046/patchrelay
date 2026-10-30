package dev.patchrelay.screens;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.junit.Assert.*;

public class ScreenManifestsTest {
    @Test public void authenticatesAndBindsTheScreenContract() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String key = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        JSONObject value = new JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
            .put("appId", "example.app").put("baseVersion", "base1").put("version", 1)
            .put("hostSha256", String.join("", Collections.nCopies(64, "b"))).put("screenIds", new JSONArray().put("checkout")).put("sha256", String.join("", Collections.nCopies(64, "a")));
        assertEquals(1, ScreenManifests.INSTANCE.verify(sign(value, keys), key, "example.app", "base1", Collections.singleton("checkout"), String.join("", Collections.nCopies(64, "b"))).getLong("version"));
        for (Object[] change : new Object[][] {{"schema", 1}, {"screenApi", 1}, {"runtime", "unknown"},
                {"appId", "other.app"}, {"baseVersion", "base2"}, {"version", 0},
                {"screenIds", new JSONArray().put("other")}, {"hostSha256", String.join("", Collections.nCopies(64, "c"))}, {"sha256", "not-a-hash"}}) {
            String field = (String) change[0]; Object previous = value.get(field);
            value.put(field, change[1]); rejected(sign(value, keys), key); value.put(field, previous);
        }
        JSONObject envelope = new JSONObject(new String(sign(value, keys), StandardCharsets.UTF_8));
        envelope.put("payload", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
        rejected(envelope.toString().getBytes(StandardCharsets.UTF_8), key);
    }

    @Test public void rejectsAlteredArtifactAndInvalidUtf8() throws Exception {
        try { ScreenManifests.INSTANCE.checkHash(new byte[]{1}, String.join("", Collections.nCopies(64, "a"))); fail("Accepted altered program"); }
        catch (SecurityException expected) { }
        try { ScreenManifests.INSTANCE.decode(new byte[]{(byte) 0xff}); fail("Accepted invalid UTF-8"); }
        catch (Exception expected) { }
    }

    private static void rejected(byte[] bytes, String key) throws Exception {
        try { ScreenManifests.INSTANCE.verify(bytes, key, "example.app", "base1", Collections.singleton("checkout"), String.join("", Collections.nCopies(64, "b"))); fail("Accepted incompatible/altered manifest"); }
        catch (IllegalArgumentException | SecurityException expected) { }
    }
    private static byte[] sign(JSONObject value, KeyPair keys) throws Exception {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(keys.getPrivate()); signer.update(bytes);
        return new JSONObject().put("payload", Base64.getEncoder().encodeToString(bytes))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().getBytes(StandardCharsets.UTF_8);
    }
}
