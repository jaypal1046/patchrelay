import dev.patchrelay.Patch;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Run with the SDK and old Kotlin runtime on the JVM classpath. */
public final class CheckEmbeddedUpgrade {
    public static void main(String[] args) throws Exception {
        URL patchJar = Path.of(args[0]).toUri().toURL();
        URL sdkJar = Path.of(args[1]).toUri().toURL();
        String entry = "dev.patchrelay.demo.patch.EmbeddedUpgradePatch";
        // Same parent-first structure as the Android SDK: host Kotlin remains authoritative.
        try (URLClassLoader child = new URLClassLoader(new URL[]{patchJar}, Patch.class.getClassLoader())) {
            Patch patch = (Patch) child.loadClass(entry).getDeclaredConstructor().newInstance();
            String result = patch.invoke("greet", "test");
            if (!result.contains("Runtime Kotlin: 1.9.23") || !result.contains("Library loader: parent/app")
                    || !result.contains("Result: NOT UPGRADED")) throw new AssertionError(result);
            System.out.println("Parent-first check: host runtime 1.9.23 wins.");
        }
        // Independently prove the very same jar really contains the newer runtime.
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{patchJar, sdkJar}, ClassLoader.getPlatformClassLoader())) {
            Object patch = isolated.loadClass(entry).getDeclaredConstructor().newInstance();
            String result = (String) patch.getClass().getMethod("invoke", String.class, String.class).invoke(patch, "greet", "test");
            if (!result.contains("Runtime Kotlin: 2.0.21") || !result.contains("Library loader: patch")
                    || !result.contains("Result: UPGRADED")) throw new AssertionError(result);
            System.out.println("Isolated check: bundled runtime is genuinely 2.0.21.");
        }
    }
}
