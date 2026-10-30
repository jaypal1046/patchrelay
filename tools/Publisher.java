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
        if (args.length != 9 || !args[0].equals("sign")) {
            throw new IllegalArgumentException("Usage: keygen PRIVATE PUBLIC | sign PRIVATE JS APP_ID BASE_VERSION PATCH_VERSION SCREEN_IDS HTTPS_URL OUTPUT");
        }
        long version = Long.parseLong(args[5]);
        if (version <= 0) throw new IllegalArgumentException("Positive patch version required");
        java.net.URI uri = java.net.URI.create(args[7]);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Public HTTPS artifact URL required");
        byte[] program = Files.readAllBytes(Path.of(args[2]));
        if (program.length == 0 || program.length > 2 * 1024 * 1024)
            throw new IllegalArgumentException("Expected UTF-8 screen program, maximum 2 MiB");
        StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(program));
        String[] screens = args[6].split(",", -1);
        if (screens.length == 0 || screens.length > 16 || java.util.Arrays.stream(screens).distinct().count() != screens.length)
            throw new IllegalArgumentException("Invalid screen IDs");
        for (String screen : screens) if (!screen.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Invalid screen ID");
        String screenJson = java.util.Arrays.stream(screens).map(Publisher::quote).collect(java.util.stream.Collectors.joining(","));
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(program)) hash.append(String.format("%02x", b & 255));
        String payload = "{\"schema\":3,\"screenApi\":1,\"runtime\":\"kotlin-screen-js\",\"appId\":" + quote(args[3])
                + ",\"baseVersion\":" + quote(args[4]) + ",\"version\":" + version
                + ",\"screenIds\":[" + screenJson + "],\"artifactUrl\":" + quote(args[7])
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
