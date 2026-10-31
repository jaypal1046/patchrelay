# PatchRelay Android — API overview

Experimental Android library, minSdk 26. This is a documentation notice, not
generated Javadoc or a complete integration guide.

- ScreenUpdates verifies signed, exact-base programs, stages them for a later
  process, selects startup fallback and tracks health. Run storage/network work
  off the main thread.
- ScreenEngine runs bounded JavaScript for a registered screen. Register native
  host functions before start; validate their arguments and authorize operations.
- NativeScreenRenderer renders supported screen data using Android widgets.
- IntPatchRunner connects registered integer hooks to optional patches and native
  fallback. Arbitrary compiled Kotlin or Compose replacement is unsupported.

The app supplies its own trust keys, baseline program, compatibility identity,
screen IDs and lifecycle integration. Do not put credentials in JavaScript.
No Google Play approval or production stability is promised.

Usage guides included in this archive: docs/integration.md,
docs/gradle-integration.md and docs/update-routing.md. These document public APIs
and setup; implementation source is not included. Some linked development-only
documents require repository access: https://github.com/jaypal1046/patchrelay
