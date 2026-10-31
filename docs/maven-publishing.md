# Maven distribution

Status: submitted through the Central Portal; latest observed deployment status
is `PUBLISHING`, not yet verified publicly downloadable. Coordinates:
`io.github.jaypal1046:patchrelay-android:0.1.0-alpha01`.
Namespace `io.github.jaypal1046` was verified in the signed-in Central Portal on
2026-10-31. This is the Android SDK, independent of CLI alpha04 and demo APK 0.8.0.

Local verification (2026-10-31): obfuscated release Maven publication and demo
R8 build passed. A separate Android project resolved the local Maven coordinate,
compiled Kotlin calls/default arguments against the packaged AAR and built its
own R8 release APK successfully. Archive inspection confirmed implementation
class renaming, retained public API names, private-source notice JARs and no
source/map/key files or demo/test classes in the publication. These builds were
not installed on a phone. Both missing-license and missing-PGP-key bundle guards
were exercised and refused to produce an upload. Signed-bundle creation and
Central server validation were initially untested. Signed-bundle creation now
passes: six artifacts have valid PGP signatures/checksums, the public key is
retrievable from keys.openpgp.org, and a separate R8 consumer build compiles the
catalog API and Kotlin defaults from the obfuscated AAR. No server deployment
was initially submitted by automation because Chrome file-chooser handling timed
out. The publisher subsequently submitted it manually; deployment
`0dac37c3-c1bd-415a-acd7-ac4b95c2f130` is now `PUBLISHING`. Last public repository
check still returned 404, so do not claim consumer availability yet.

Verified upload ZIP SHA-256:
`372e418cf8c54a153df8ad4933714357625242cd9a174174d489267ac536e8af`.

## Build a private local preview

With JDK 17:

```powershell
.\gradlew.bat :sdk:publishReleasePublicationToPreviewRepository :demo:assembleR8Check
```

Repository output: `sdk/build/maven-repository`. This contains the AAR, POM,
Gradle metadata and transitive Kotlin dependency information. Companion sources
archive contains a private-source notice and license, never implementation
source. The Javadoc archive includes public API/setup guides. A signed license
text artifact provides the release's public license URL after publication.
A file repository can be used for local integration testing:

```kotlin
// Consumer settings.gradle.kts: dependencyResolutionManagement.repositories
maven { url = uri("C:/Jay/dev/AutoUpdate/sdk/build/maven-repository") }
```

```kotlin
// Consumer app/build.gradle.kts
dependencies {
    implementation("io.github.jaypal1046:patchrelay-android:0.1.0-alpha01")
}
```

Do not replace an already distributed version. This preview is a prototype,
not a production stability promise.

## Public Maven Central release

1. Create a Central Portal account and verify `io.github.jaypal1046`, or choose
   another namespace you own.
2. The selected [PatchRelay SDK License — Binary Use](../LICENSE) is attached as
   the signed `license.txt` classifier artifact; the POM links to its canonical
   Maven Central URL, available once this release is published. No separate public
   website or source repository is required for the license. It permits royalty-free integration in free
   and commercial applications while keeping SDK source private. Standalone SDK
   resale, publishing modified SDKs and reverse engineering are restricted, with
   an exception for rights required by applicable law. Build-time shrinking and
   obfuscation are expressly permitted. These custom terms have not been reviewed
   by a lawyer; enforceability depends on applicable law.
3. Create a separate PGP publication key. A Portal token is needed only for
   automated uploads; manual upload uses the signed-in browser. Keep credentials out
   of Git, AARs and consumer apps. These are separate from app patch-signing keys.
4. Supply approved license metadata and signing credentials to the configured
   bundle task below. Central explicitly permits placeholder
   archives containing a README when sources cannot be supplied. Clearly state
   that sources are private; never accidentally enable `withSourcesJar()`.
5. Validate the publication, test a separate consuming app with release R8 and
   the real patch flow, then upload the approved immutable version.

After publication, consumers use `mavenCentral()` and the dependency above.
Until then, those coordinates will not resolve from Central. GitHub Packages
requires consumer authentication, making it less convenient for unrestricted use.

## Exact upload procedure

The deployment page asks for a deployment name, optional description and a file.
Use:

- Deployment name: `PatchRelay Android 0.1.0-alpha01`
- Description: `Alpha Android SDK for native screen rendering and bounded JavaScript patches. Private-source Android library.`
- File: `sdk/build/central/patchrelay-android-0.1.0-alpha01-central.zip`

That ZIP now exists and passed local verification. Upload the exact verified ZIP,
not the raw AAR or preview repository directory. It contains no private keys,
passphrase files, app source archives or R8 mappings.

On this Windows account, rebuild with:

```powershell
.\tools\build-maven-release.ps1
```

The helper loads the existing encrypted PGP key and Windows-DPAPI-protected
passphrase into a single-use Gradle process, then restores/removes those process
environment values. `-CreateSigningKey` is for first setup only and refuses
overwriting existing keys. `-ResumeKeySetup` only handles an explicitly incomplete
generation stage. Do not regenerate keys merely because a build failed.

The public signing fingerprint is
`0C2D27B1079CF4F0F8AF5711826044C82B8A81E9`, published to keys.openpgp.org.
The private material and revocation certificate remain in ignored `.keys/`.
Back them up securely; DPAPI recovery requires the original Windows identity.
Plan a portable encrypted key/passphrase backup before replacing/resetting this
account. This software key setup is not hardware-backed key management.

After supplying `MAVEN_SIGNING_KEY` (ASCII-armored private PGP key) and
`MAVEN_SIGNING_PASSWORD` through a protected local environment or CI secret store:

```powershell
.\gradlew.bat :sdk:centralBundle --no-daemon
```

The default POM license URL points to this version's attached license text. A
separately hosted URL can be supplied with `-PpublicationLicenseUrl=...` if desired.
The POM license name defaults to `PatchRelay SDK License — Binary Use`.
The task writes no credentials into the bundle and fails when signing/license
details are missing. Never use the patch-signing RSA key as the publication key.
Publish the PGP public key to a Central-supported keyserver and confirm that it
can be retrieved before uploading; keep its private key private.

The ZIP preserves Maven's `io/github/jaypal1046/patchrelay-android/0.1.0-alpha01/`
folder structure and contains only that version's AAR, POM, Gradle module metadata,
documentation/notice archives, license text, detached signatures and checksums. Do not ZIP the whole project,
an APK, `.keys`, a mapping file or the raw AAR alone.

On Central, verify the Namespace tab, choose Publish Component, enter the fields
above and select this ZIP. Submit it for validation; inspect any errors before
the final public publish action. Central publication is immutable: do not release
the version until its contents and consumer integration have been reviewed.
Do not infer availability merely from a successful file upload; check deployment
status and resolve the dependency from a clean consumer afterwards.

See [official bundle upload instructions](https://central.sonatype.org/publish/publish-portal-upload/)
and [PGP signing requirements](https://central.sonatype.org/publish/requirements/#sign-files-with-gpgpgp).

## ProGuard / R8 and source privacy

The AAR embeds `consumer-rules.pro`, automatically applied by consumer R8.
No broad keep rules are required today: SDK integration uses direct calls,
JSON and `evaluateJavascript`, not reflection or `addJavascriptInterface`.
Consumers should enable `isMinifyEnabled = true` in release builds and use
`proguard-android-optimize.txt`. The `r8Check` demo variant checks compilation
without changing the ordinary release variant or installing anything.

Release AARs now run through R8 before Maven packaging. Producer rules preserve
the ten existing public API classes and their public/protected members while
allowing implementation classes and private members to shrink/rename. Kotlin
metadata is retained for named arguments, default parameters and constructors.
Consumer R8 can further optimize the final APK. The SDK's `proguard-rules.pro`
is producer configuration; `consumer-rules.pro` is packaged for consumer builds.

Keep `sdk/build/outputs/mapping/release/mapping.txt` private and archive it by
release for crash debugging. The upload task allowlists only publication files;
mapping files, local projects, APKs and keys are not part of that allowlist.

A private repository protects original source access. An AAR/APK can still be
decompiled; R8 increases inspection effort but cannot prevent reverse engineering.
JavaScript and HTML assets also remain inspectable. Never ship private keys,
server secrets or credentials in either native code or patches.

Do not encrypt the Maven AAR: standard Android builds cannot consume an encrypted
archive. Encrypting runtime assets with a decryption key inside the same SDK does
not provide a confidentiality boundary. This release uses R8, source omission and
private mappings; it does not claim encrypted executable code or complete secrecy.

## What is excluded, and what remains visible

The built artifacts were inspected: no Kotlin/Java implementation source, source
maps, key/keystore files, demo classes or instrumentation classes are packaged.
The sources JAR contains a notice and LICENSE; the Javadoc JAR adds public
integration/build/routing guides, with no implementation source. The same license
is packaged under `META-INF/patchrelay/LICENSE` in the AAR's classes JAR.
The AAR includes the hidden
interpreter HTML, bytecode, manifest and consumer rules needed at runtime.
This is an archive-content check, not a complete secret-detection audit.

Public package/class names, compiled constants, method behavior, Kotlin metadata,
debug information and interpreter logic can still aid inspection. R8 obfuscates
the release AAR's implementation; this is not encryption. Removing API names/metadata
blindly can break Kotlin consumers and serialization/tooling; no such promise is
made here. Public Maven coordinates, dependency metadata, signatures and license
information are required distribution information and must remain accurate.

Repository privacy, source omission, signature verification and obfuscation serve
different purposes. Signature verification protects update authenticity; it does
not hide the SDK or JS. The documentation and license artifacts give consumers
usage information without private-repository access. A public documentation site
remains useful but is not required for this prepared bundle.

Publication/build configuration participates in the demo native fingerprint.
Producer and consumer ProGuard rule files are included in `runtime.json` inputs.
Regenerate an unpublished base snapshot before publishing matching patches;
never reuse a previously installed base identity after native changes.

References: [Central requirements](https://central.sonatype.org/publish/requirements/),
[Android library optimization](https://developer.android.com/topic/performance/app-optimization/library-optimization),
[GitHub Gradle registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry).
