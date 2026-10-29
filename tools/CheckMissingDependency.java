import dev.patchrelay.Patch;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Host classpath must contain the SDK and Kotlin runtime, but not Commons Codec. */
public final class CheckMissingDependency {
    public static void main(String[] args) throws Exception {
        for (int index = 0; index < 2; index++) {
            try (URLClassLoader child = new URLClassLoader(new URL[]{Path.of(args[index]).toUri().toURL()}, Patch.class.getClassLoader())) {
                Patch patch = (Patch) child.loadClass("dev.patchrelay.demo.patch.MissingDependencyPatch").getDeclaredConstructor().newInstance();
                String result = patch.invoke("greet", "hello");
                if (index == 0) {
                    if (!result.contains("Result: MISSING") || !result.contains("NoClassDefFoundError") || !result.contains("DigestUtils")) throw new AssertionError(result);
                    System.out.println("Thin patch: actual missing Codec class confirmed.");
                } else {
                    if (!result.contains("Result: AVAILABLE") || !result.contains("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")) throw new AssertionError(result);
                    System.out.println("Same code with bundled Codec: known hash correct.");
                }
            }
        }
    }
}
