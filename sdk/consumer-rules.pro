-keep public interface dev.patchrelay.Patch { *; }
-keep class dev.patchrelay.PatchRelay { public *; }
# Future patches reference Kotlin runtime names beyond the installed app's usage.
-keep class kotlin.** { *; }
