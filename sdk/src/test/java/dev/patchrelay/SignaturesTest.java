package dev.patchrelay;

import org.junit.Test;
import java.security.*;
import java.util.Base64;
import static org.junit.Assert.*;

public class SignaturesTest {
    @Test public void rejectsTamperedPayloadAndWrongKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] payload = "original patch metadata".getBytes("UTF-8");
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        Signatures.verify(payload, signature, publicKey);
        try { Signatures.verify("tampered".getBytes("UTF-8"), signature, publicKey); fail("Accepted tampered metadata"); }
        catch (SecurityException expected) { }
        String otherKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        try { Signatures.verify(payload, signature, otherKey); fail("Accepted wrong signing key"); }
        catch (SecurityException expected) { }
    }
}
