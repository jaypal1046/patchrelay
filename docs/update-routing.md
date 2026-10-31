# App, build and channel routing

The installed app checks one stable signed catalog URL for its application and
channel. It does not enumerate GitHub releases or install every available patch.
Configure the prepared demo's ignored `local.properties`:

```properties
patchrelay.catalogUrl=https://updates.example.com/com.example.app/production.json
patchrelay.channel=production
```

`patchrelay init` asks for these settings; external hosts need the corresponding
Gradle BuildConfig generation and lifecycle integration described in the
[integration guide](integration.md). The SDK does not read local.properties itself.

## Files and commands

1. With the Gradle plugin applied, a normal `assembleRelease` or `bundleRelease`
   records the actual base automatically; `patchrelay baseline` is an optional
   developer-named APK workflow. See [normal-build integration](gradle-integration.md).
2. After supported Kotlin changes, `patchrelay patch` requires an explicitly
   selected baseline, compiles the complete current program and signs its manifest.
3. `patchrelay catalog` requires the selected patch, prior signed catalog (or
   explicit `new`), channel and final manifest URL. It signs a new local catalog.
4. Upload `screens.js` and its signed manifest to immutable URLs first. Verify
   those downloads, then replace the catalog at the app's stable URL. Commands
   do not perform remote uploads, GitHub login or token management.

Different apps can share a GitHub artifact repository. Default patch tags include
a hash of app ID, base label and full native fingerprint, preventing the old
`base-patch-1` collision. Each app/channel has its own catalog; each app owns its
signing keys. App A's catalog or patch is rejected by App B.

## Signed catalog format

Like patch manifests, a catalog is a JSON envelope with Base64 `payload` and
`signature`, signed using SHA256withRSA. The decoded catalog payload is:

```json
{
  "schema": 1,
  "kind": "patchrelay-catalog",
  "appId": "com.example.app",
  "channel": "production",
  "entries": [
    {
      "baseVersion": "android-v1",
      "hostSha256": "<64 lowercase hexadecimal characters>",
      "version": 7,
      "manifestUrl": "https://updates.example.com/releases/v1-patch-7/patch.json"
    },
    {
      "baseVersion": "android-v2",
      "hostSha256": "<different 64-character fingerprint>",
      "version": 1,
      "manifestUrl": "https://updates.example.com/releases/v2-patch-1/patch.json"
    }
  ]
}
```

The placeholders above illustrate the format, not a valid signed upload. Catalogs
are limited to 64 KiB including the envelope and 64 distinct exact-base routes.
Duplicate routes, invalid versions, non-HTTPS URLs and wrong app/channel values
are rejected. A matching route must have the installed base label and full host
fingerprint. The selected manifest must independently pass signature, app, base,
screen API/IDs and artifact-hash checks; its version must match the catalog.
No matching route means no update. Only that route's artifact is downloaded.

## Cumulative fixes and version records

Programs are full supported-screen snapshots. If patch 7 includes fixes 1–6 in
current source, a user on patch 2 can download patch 7 directly. No intermediate
patch chain runs. Database migrations, persistent business-state changes and
native dependencies are not handled by this mechanism.

The CLI keeps a locked local high-water record for each app/base/fingerprint,
including builds using custom output folders. Reserved versions are not reused
after output failure. Back up `.patchrelay/versions` and baseline directories.
The catalog updater refuses a version decrease for an existing route, provided
the developer supplies the actual latest catalog. Local state cannot know what
another publisher machine released without shared release records. SDK high-water
state blocks already-seen versions, but cannot prove an old, valid signed catalog
is fresh on a brand-new installation. A malicious host can also withhold updates.
All channels share the exact-base patch version sequence.

## Play Store/native upgrades

On base fingerprint/identity change, SDK patch selection/high-water/startup state
resets. The new APK starts with its own bundled program offline; it never executes
the previous APK's patch. Later catalog checks select only the new native base.
The new base's patch 1 can follow the old base's patch 7. SDK preferences reset
does not clear login/business preferences or migrate databases. Old patch files
are left in their separate base directories; disk-retention cleanup is pending.

The new demo checks silently once per process after healthy rendering, on a worker.
Downloaded programs activate on a later process start, not mid-session. There is
no periodic scheduler, forced restart or foreground security-check Activity.

Local tests cover exact-base selection, jumping to version 7, app/channel
rejection, signature tampering, duplicate routes, catalog/manifest mismatch and
native upgrades preserving business preferences (including reused base labels
with different fingerprints). These are unit/Robolectric and synthetic Gradle
fixture results, not fresh physical-device or Google Play upgrade proof.

Private GitHub repositories still need an accessible delivery endpoint or a
separate authenticated gateway. Do not embed repository tokens in APKs or URLs.
Store eligibility is not established by this protocol.
