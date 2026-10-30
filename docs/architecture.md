# Architecture

PatchRelay currently targets native Android apps written in Java or Kotlin. Both share Android's DEX runtime and one SDK. React Native and Flutter are outside project scope; iOS native support remains unimplemented.

The existing DEX updater is an experimental direct-distribution mechanism. Downloading executable DEX outside Play conflicts with Google Play's published restriction. A Play-compatible replacement for arbitrary installed Java/Kotlin code has not been implemented.

## Ownership

| Location | Responsibility |
| --- | --- |
| `sdk/` | HTTPS download, signature/compatibility verification, DEX staging, activation and startup rollback; distributed as one AAR |
| `tools/Publisher.java` | Signing keys and schema-1 DEX manifest production |
| `tools/PatchBuild.psm1` | Shared DEX compilation and signing commands |
| `tools/build-patch.ps1` | Normal Android patch build entry point |
| `tools/publish-github.ps1` | Release hosting, separate from on-device activation |
| `demo/` | Native Android integration and visible image test |
| `patch/` | Example implementation and stress-test fixtures |
| `visual/` | Historical separate-app comparison fixtures |
| Other `tools/build-*.ps1` | Scenario-specific checks using shared build commands |

Verification helpers live inside `sdk/`; apps do not need a separate core AAR. Existing `dev.patchrelay.Patch` and `PatchRelay` contracts, schema-1 manifests, and native release URLs remain unchanged.

## Update lifecycle

1. Host provides app identity, exact native base compatibility identifier, trusted signing key and HTTPS manifest URL.
2. Verify the signed payload before trusting metadata; reject incompatible bases and non-increasing versions.
3. Download bounded DEX bytes, verify SHA-256 and stage durably for the next process launch.
4. Load the prepared implementation through the stable `Patch` interface. Confirm health only after exercising patched startup.
5. Restore the previous or built-in implementation after unconfirmed startup or failed loading. Preserve the accepted-version high-water mark.

Schema **1** contains `schema`, `apiVersion`, `appId`, `baseVersion`, `version`, `entryPoint`, `artifactUrl` and `sha256`. The envelope contains Base64 `payload` and `signature`; sign exact UTF-8 payload bytes using SHA256withRSA. Unsupported schemas are rejected.

## Boundaries

This is prepared-interface patching, not arbitrary method replacement. APK-embedded dependencies remain parent-first. New manifest components, permissions and unsupported native changes require a new base app. Image updates use an explicit downloaded overlay rather than mutating APK resources.

Health confirmation covers startup. Later failures need app-managed handling or a corrective patch. Code rollback does not undo files, databases or external side effects. One designated app process only; key rotation/revocation and broader device compatibility remain pending.

## Packaging verification

After building `:patch:jar`, run on Windows:

```powershell
.\tools\test-patch-build.ps1 -AndroidSdk $env:ANDROID_HOME
```

The self-check creates local test keys and verifies DEX generation, signatures, metadata and signed-output overwrite protection. It does not upload releases or install apps.
