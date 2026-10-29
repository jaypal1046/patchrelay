# Preserve the old dependency exactly so tests can measure parent-first loading.
-keep class com.google.gson.** { *; }
# SDK consumer rules preserve the Patch ABI used by separately compiled DEX.
