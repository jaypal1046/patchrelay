# Preserve the supported consumer API; implementation classes remain obfuscatable.
-keep public class dev.patchrelay.Signatures { public protected *; }
-keep public class dev.patchrelay.core.HttpsDownloads { public protected *; }
-keep public class dev.patchrelay.screens.ScreenEngine { public protected *; }
-keep public class dev.patchrelay.screens.ScreenUpdates { public protected *; }
-keep public class dev.patchrelay.screens.ScreenProgram { public protected *; }
-keep public class dev.patchrelay.screens.ScreenNode { public protected *; }
-keep public class dev.patchrelay.screens.ScreenTree { public protected *; }
-keep public class dev.patchrelay.screens.ScreenTrees { public protected *; }
-keep public class dev.patchrelay.screens.NativeScreenRenderer { public protected *; }
-keep public class dev.patchrelay.screens.IntPatchRunner { public protected *; }

# Kotlin consumers need metadata for constructors, named arguments and defaults.
-keep class kotlin.Metadata { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
