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

## Hosting setup: personal server or GitHub Releases

Pick one stable public HTTPS catalog URL for each app and channel. Put that exact
URL in the APK's native configuration before the first production build. For
example:

```properties
patchrelay.catalogUrl=https://updates.example.com/myapp/production/catalog.json
patchrelay.channel=production
```

For a personal server, configure these values during `patchrelay init`:

```text
patchBaseUrl = https://updates.example.com/myapp/patches
catalogUrl   = https://updates.example.com/myapp/production/catalog.json
```

The patch base URL is a directory prefix. Every patch run gets a unique tag
folder; never upload a later patch over an earlier folder. For example, after
three releases your server layout looks like this:

```text
/srv/www/updates/myapp/
├── production/
│   └── catalog.json                 # same URL every time; replace after upload
└── patches/
    ├── patch-a1b2-1/
    │   ├── screens.js
    │   └── patch.json
    ├── patch-a1b2-2/
    │   ├── screens.js
    │   └── patch.json
    └── patch-c3d4-1/                # another installed APK base
        ├── screens.js
        └── patch.json
```

The exact generated tag includes a route hash and version; use the URL printed
by `patch`, not the shortened illustrative names above. For a tag like
`patch-a1b2-4`, upload files to these exact locations:

| Local generated file | Remote path |
| --- | --- |
| `screens.js` | `https://updates.example.com/myapp/patches/patch-a1b2-4/screens.js` |
| `patch.json` | `https://updates.example.com/myapp/patches/patch-a1b2-4/patch.json` |
| `catalog.json` | `https://updates.example.com/myapp/production/catalog.json` |

Create the new tag-named folder on the server and upload `screens.js` and
`patch.json` there first. Upload the new `catalog.json` to its stable path last.
The catalog is one signed discovery file per app/channel, not one file per
patch. Each update replaces that same catalog; its entries point to the latest
patch for each supported installed APK base. Keep the referenced tag folders
unchanged. Split different apps or channels into separate catalog paths. Replace
the catalog atomically if the server supports it. Configure the
server/CDN to serve these files over HTTPS without login pages, cookies, or
interactive redirects. Verify each URL returns the actual JSON/JS file to an
unauthenticated HTTPS GET.

The CLI cleans and reuses its local `build/patchrelay/patches` output directory
on each run. Do not mirror that whole local directory over the server's `patches`
directory or use a sync option that deletes remote files. Copy only the new
release's two files into its new tag folder; this preserves older published
patches that existing catalogs may still need.

For GitHub Releases, use `node ..\\cli\\bin\\patchrelay.mjs patch --github`.
It creates a release and uploads the same three assets. Patch files use the
release's immutable tag URL. The app checks the stable URL
`https://github.com/OWNER/REPO/releases/latest/download/catalog.json`; put that
URL in the APK before building/installing it. GitHub credentials are used by the
CLI to publish and are never sent by the app. GitHub releases used by the app
must be publicly downloadable.

Do not configure the APK with the patch base URL or a GitHub Releases page. The
APK needs the catalog file URL. The catalog points to `patch.json`, which points
to `screens.js`. Changing the catalog URL later requires a new APK installation;
publishing another patch at the same catalog URL does not.

## Files and commands

1. With the Gradle plugin applied, a normal `assembleRelease` or `bundleRelease`
   records the actual base automatically; `patchrelay baseline` is an optional
   developer-named APK workflow. See [normal-build integration](gradle-integration.md).
2. After supported Kotlin changes, `patchrelay patch` requires an explicitly
   selected baseline, compiles the complete current program and signs its manifest.
3. `patchrelay catalog` requires the selected patch, prior signed catalog (or
   explicit `new`), channel and final manifest URL. It signs a new local catalog.
4. Host `screens.js` and its signed manifest at immutable HTTPS URLs first,
   verify those downloads, then replace the catalog at the app's stable URL.
   `patch --github` automates the GitHub Releases path; other servers use the
   exact destinations printed by the command.

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
