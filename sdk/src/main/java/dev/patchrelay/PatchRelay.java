package dev.patchrelay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.AtomicFile;
import dalvik.system.DexClassLoader;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;

/** Experimental, single-process patch loader for directly distributed Android apps. */
public final class PatchRelay {
    private static final int MAX_MANIFEST = 64 * 1024;
    private static final int MAX_PATCH = 16 * 1024 * 1024;
    private final Context context;
    private final String baseVersion, publicKey;
    private final File directory;
    private final SharedPreferences state;
    private boolean started;
    private long sessionVersion;

    public PatchRelay(Context context, String baseVersion, String publicKey) throws IOException {
        this.context = context.getApplicationContext();
        if (baseVersion == null || baseVersion.isEmpty()) throw new IllegalArgumentException("Base version required");
        this.baseVersion = baseVersion;
        this.publicKey = publicKey;
        directory = new File(new File(this.context.getNoBackupFilesDir(), "patchrelay"),
                java.util.UUID.nameUUIDFromBytes(baseVersion.getBytes(StandardCharsets.UTF_8)).toString());
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create patch directory");
        state = this.context.getSharedPreferences("patchrelay", Context.MODE_PRIVATE);
        if (!baseVersion.equals(state.getString("base", ""))) {
            if (!state.edit().clear().putString("base", baseVersion).commit()) throw new IOException("Cannot reset patch state");
        }
    }

    /** Downloads and stages an update. Run on a worker thread; takes effect next process launch. */
    public synchronized boolean stage(String manifestUrl) throws Exception {
        workerThread();
        byte[] envelope = download(manifestUrl, MAX_MANIFEST);
        JSONObject manifest = verify(envelope);
        long version = manifest.getLong("version");
        if (version <= state.getLong("highest", 0)) return false;
        byte[] dex = download(manifest.getString("artifactUrl"), MAX_PATCH);
        checkHash(dex, manifest.getString("sha256"));
        File target = new File(directory, version + ".dex");
        // Android 14 requires read-only code files before their contents are written.
        if (target.exists()) throw new IOException("Patch file already exists");
        try (FileOutputStream stream = new FileOutputStream(target)) {
            if (!target.setReadOnly()) throw new IOException("Cannot protect patch file");
            stream.write(dex);
            stream.getFD().sync();
        } catch (Exception error) {
            target.delete();
            throw error;
        }
        try {
            write(new File(directory, version + ".json"), envelope);
            if (!state.edit().putLong("highest", version).putLong("pending", version).commit())
                throw new IOException("Cannot save staged update");
        } catch (Exception error) {
            target.delete();
            new File(directory, version + ".json").delete();
            throw error;
        }
        return true;
    }

    /** Call once per process, before invoking patchable logic. */
    public synchronized Patch start(Patch builtIn) throws Exception {
        workerThread();
        if (builtIn == null) throw new IllegalArgumentException("Built-in implementation required");
        if (started) throw new IllegalStateException("start must run once per process");
        started = true;
        long active = state.getLong("active", 0);
        if (state.getBoolean("booting", false)) {
            active = state.getLong("previous", 0);
            save(state.edit().putLong("active", active).putLong("previous", 0).putBoolean("booting", false));
        }
        long pending = state.getLong("pending", 0);
        if (pending > 0) {
            save(state.edit().putLong("previous", active).putLong("active", pending).remove("pending"));
            active = pending;
        }
        if (active == 0) return builtIn;
        // A killed or crashing process before markHealthy() rolls back on next launch.
        save(state.edit().putBoolean("booting", true));
        try {
            Patch loaded = load(active);
            sessionVersion = active;
            return loaded;
        } catch (Exception | LinkageError failure) {
            long previous = state.getLong("previous", 0);
            save(state.edit().putLong("active", previous).putLong("previous", 0).putBoolean("booting", false));
            if (previous != 0 && previous != active) {
                save(state.edit().putBoolean("booting", true));
                try {
                    Patch loaded = load(previous);
                    sessionVersion = previous;
                    return loaded;
                } catch (Exception | LinkageError ignored) {
                    save(state.edit().putLong("active", 0).putBoolean("booting", false));
                }
            }
            android.util.Log.e("PatchRelay", "Rejected patch; restored fallback", failure);
            return builtIn;
        }
    }

    /** Call only after the app has exercised its patched startup path successfully. */
    public synchronized void markHealthy() throws IOException {
        if (!started) throw new IllegalStateException("Call start first");
        if (sessionVersion != 0) save(state.edit().putBoolean("booting", false));
    }

    public synchronized long activeVersion() { return sessionVersion; }

    private Patch load(long version) throws Exception {
        JSONObject manifest = verify(Files.readAllBytes(new File(directory, version + ".json").toPath()));
        if (manifest.getLong("version") != version) throw new SecurityException("Version mismatch");
        File dex = new File(directory, version + ".dex");
        if (dex.length() > MAX_PATCH) throw new SecurityException("Patch too large");
        checkHash(Files.readAllBytes(dex.toPath()), manifest.getString("sha256"));
        DexClassLoader loader = new DexClassLoader(dex.getAbsolutePath(), context.getCodeCacheDir().getAbsolutePath(),
                null, Patch.class.getClassLoader());
        Class<?> implementation = loader.loadClass(manifest.getString("entryPoint"));
        if (implementation.getClassLoader() != loader) throw new SecurityException("Entry point must come from patch");
        return implementation.asSubclass(Patch.class).getDeclaredConstructor().newInstance();
    }

    private JSONObject verify(byte[] envelope) throws Exception {
        if (envelope.length > MAX_MANIFEST) throw new SecurityException("Manifest too large");
        JSONObject signed = new JSONObject(new String(envelope, StandardCharsets.UTF_8));
        byte[] payload = Base64.getDecoder().decode(signed.getString("payload"));
        Signatures.verify(payload, Base64.getDecoder().decode(signed.getString("signature")), publicKey);
        JSONObject manifest = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        if (manifest.getInt("schema") != 1 || manifest.getInt("apiVersion") != 1
                || !context.getPackageName().equals(manifest.getString("appId"))
                || !baseVersion.equals(manifest.getString("baseVersion")) || manifest.getLong("version") <= 0)
            throw new SecurityException("Patch incompatible with installed app");
        return manifest;
    }

    private static byte[] download(String address, int limit) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null) throw new IOException("HTTPS URL required");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect location");
                    url = new URL(url, location);
                    continue;
                }
                if (status != 200) throw new IOException("Download HTTP " + status);
                if (connection.getContentLengthLong() > limit) throw new IOException("Download too large");
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > limit) throw new IOException("Download too large");
                        out.write(buffer, 0, count);
                    }
                    return out.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }

    private static void checkHash(byte[] data, String expected) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!hex.toString().equals(expected)) throw new SecurityException("Patch hash mismatch");
    }

    private static void write(File file, byte[] data) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = atomic.startWrite();
        try { out.write(data); atomic.finishWrite(out); }
        catch (IOException failure) { atomic.failWrite(out); throw failure; }
    }

    private static void save(SharedPreferences.Editor editor) throws IOException {
        if (!editor.commit()) throw new IOException("Cannot persist patch state");
    }

    private static void workerThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Use a worker thread");
    }
}
