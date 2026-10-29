# The SDK loads this class by name through reflection.
-keep public class dev.patchrelay.demo.patch.R8Patch {
    public <init>();
    public java.lang.String invoke(java.lang.String, java.lang.String);
}
# Retain the helper so its actual renamed class is visible on the phone.
-keep,allowobfuscation class dev.patchrelay.demo.patch.PatchFormatter { *; }
