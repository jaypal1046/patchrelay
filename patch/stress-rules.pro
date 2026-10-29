# Reflection loads this entry point; its name and public ABI must survive R8.
-keep public class dev.patchrelay.demo.patch.ComplexPatch {
    public <init>();
    public java.lang.String invoke(java.lang.String, java.lang.String);
}
-dontwarn sun.misc.Unsafe
