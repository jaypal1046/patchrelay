import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Dependency-free signing tool. Run with Java 17+: java tools/Publisher.java ... */
public final class Publisher {
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("keygen")) {
            Path privatePath = Path.of(args[1]), publicPath = Path.of(args[2]);
            if (Files.exists(privatePath) || Files.exists(publicPath)) throw new IllegalArgumentException("Refusing to overwrite keys");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair keys = generator.generateKeyPair();
            Files.createDirectories(privatePath.toAbsolutePath().getParent());
            Files.createDirectories(publicPath.toAbsolutePath().getParent());
            Files.writeString(privatePath, Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()), StandardOpenOption.CREATE_NEW);
            Files.writeString(publicPath, Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
            System.out.println("Keys created. Keep private key outside Git.");
            return;
        }
        boolean javascript = args.length == 9 && args[0].equals("sign-js");
        if (args.length != 9 || (!args[0].equals("sign") && !javascript)) {
            throw new IllegalArgumentException("Usage: keygen PRIVATE PUBLIC | sign PRIVATE DEX APP_ID BASE_VERSION PATCH_VERSION ENTRY_POINT HTTPS_URL OUTPUT | sign-js PRIVATE BUNDLE APP_ID BASE_VERSION PATCH_VERSION react-native-android HTTPS_URL OUTPUT");
        }
        long version = Long.parseLong(args[5]);
        if (version <= 0) throw new IllegalArgumentException("Positive patch version required");
        java.net.URI uri = java.net.URI.create(args[7]);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Public HTTPS artifact URL required");
        byte[] dex = Files.readAllBytes(Path.of(args[2]));
        if (javascript) {
            if (!args[6].equals("react-native-android") || dex.length == 0 || dex.length > 16 * 1024 * 1024)
                throw new IllegalArgumentException("Expected React Native Android JS bundle, maximum 16 MiB");
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(dex));
        } else if (dex.length < 8 || dex.length > 16 * 1024 * 1024 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x') {
            throw new IllegalArgumentException("Expected DEX artifact, maximum 16 MiB");
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(dex)) hash.append(String.format("%02x", b & 255));
        String payload = "{\"schema\":" + (javascript ? 2 : 1) + ",\"apiVersion\":1,\"appId\":" + quote(args[3])
                + ",\"baseVersion\":" + quote(args[4]) + ",\"version\":" + version
                + (javascript ? ",\"runtime\":\"react-native-android\",\"artifactType\":\"js-bundle\"" : ",\"entryPoint\":" + quote(args[6])) + ",\"artifactUrl\":" + quote(args[7])
                + ",\"sha256\":" + quote(hash.toString()) + "}";
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(Files.readString(Path.of(args[1])).trim())));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        signer.update(bytes);
        String envelope = "{\"payload\":\"" + Base64.getEncoder().encodeToString(bytes)
                + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signer.sign()) + "\"}\n";
        Path output = Path.of(args[8]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, envelope, StandardOpenOption.CREATE_NEW);
        System.out.println("Signed patch " + version + ": " + output);
    }

    private static String quote(String value) {
        if (value.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("Control characters forbidden");
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
