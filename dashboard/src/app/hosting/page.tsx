import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Hosting guide — PatchRelay",
  description:
    "Configure PatchRelay updates on GitHub Releases or your own HTTPS server, with safe paths for repeated patch releases.",
};

const serverFiles = [
  ["production/catalog.json", "One stable URL. Replace after each successful upload."],
  ["patches/<tag-1>/screens.js + patch.json", "Keep this versioned folder."],
  ["patches/<tag-2>/screens.js + patch.json", "Every new patch gets a new tag folder."],
  ["patches/<tag-3>/screens.js + patch.json", "Retain folders referenced by the catalog."],
];

export default function HostingGuide() {
  return (
    <>
      <header className="topbar shell guide-topbar">
        <Link className="brand" href="/"><span className="mark">↗</span> PatchRelay</Link>
        <Link className="quiet-link" href="/">Back to dashboard</Link>
      </header>
      <main className="shell guide">
        <section className="guide-intro">
          <div className="eyebrow">Deployment guide</div>
          <h1>Host PatchRelay updates</h1>
          <p>Choose GitHub Releases or your own HTTPS server. Both use the same Android SDK and the same signed update flow. Your APK checks one stable catalog URL; the catalog points to a signed manifest, which points to the screen program.</p>
          <a className="button" href="#setup">Set up hosting <span>↓</span></a>
        </section>

        <section className="guide-section" id="flow">
          <div className="eyebrow">How updates reach the app · current SDK 0.1.0-alpha01</div>
          <h2>One catalog, two hosted files per patch</h2>
          <div className="guide-flow" aria-label="PatchRelay download sequence">
            <div><small>APK checks</small><code>catalog.json</code></div><span aria-hidden="true">→</span>
            <div><small>Catalog selects</small><code>patch.json</code></div><span aria-hidden="true">→</span>
            <div><small>Manifest verifies</small><code>screens.js</code></div>
          </div>
          <p>The SDK checks the app ID, update channel, installed APK base and native fingerprint. It downloads the matching full screen program and verifies signatures and hashes before staging it. The update activates after the app restarts.</p>
        </section>

        <section className="guide-section" id="setup">
          <div className="eyebrow">Step 1</div>
          <h2>Choose a stable catalog URL before building the APK</h2>
          <p>Run <code>npx patchrelay init</code>. Choose a GitHub repository or enter <code>https</code> for your own server, then provide the catalog URL and channel. The catalog URL is embedded into the APK through the native build settings.</p>
          <pre className="guide-code"><code>{`patchrelay.catalogUrl=https://updates.example.com/myapp/production/catalog.json
patchrelay.channel=production`}</code></pre>
          <p>Keep this catalog URL unchanged for that installed app and channel. If an installed APK has a different or empty URL, build and install a new APK with the chosen URL before expecting it to find updates.</p>
        </section>

        <section className="guide-section" id="integration">
          <div className="eyebrow">First-time app setup</div>
          <h2>Install both the CLI and Android SDK</h2>
          <p>The CLI builds and signs patches on the developer machine. The Android SDK runs inside the app and checks the catalog. They are separate dependencies.</p>
          <ul className="guide-checks">
            <li>Developer tools: Node.js 22+, JDK 17+, Android SDK 35 and the project Gradle wrapper.</li>
            <li>Android devices: minimum API 26 and a working Android WebView provider.</li>
            <li>Install the project CLI: <code>npm install --save-dev @orbital_mcp/patchrelay</code>.</li>
            <li>Run <code>npx patchrelay init</code>. It writes <code>patchrelay.config.json</code>, copies <code>.patchrelay-tools</code>, updates local catalog settings and prints the Gradle changes to apply.</li>
            <li>Add <code>mavenCentral()</code> and the SDK dependency to the Android app:</li>
          </ul>
          <pre className="guide-code"><code>{`dependencies {
    implementation("io.github.jaypal1046:patchrelay-android:0.1.0-alpha01")
}`}</code></pre>
          <p>Apply the generated build plugin to the app and <code>dev.patchrelay.functions</code> to the prepared shared Kotlin screen module. Add that module as an Android dependency. <code>init</code> prints the module-specific settings; applying them is a developer step. Build a release APK once with the stable catalog URL, then install it. Keep the private signing key outside Git and pin only the matching public key in the app.</p>
        </section>

        <section className="guide-section" id="scope">
          <div className="eyebrow">Patch scope</div>
          <h2>What the installed SDK can update</h2>
          <p>The SDK renders validated screen trees using native Android widgets. The hidden WebView is the JavaScript interpreter; downloaded code does not become an APK, DEX file or native library.</p>
          <div className="guide-file-list">
            <div className="guide-file"><code>Common Kotlin screens</code><span>Screen rendering, events and navigation compiled as a complete program snapshot.</span></div>
            <div className="guide-file"><code>@PatchFunction</code><span>Public top-level functions with supported scalar values: Int, finite Double, Boolean, String, nullable scalars and Unit. Up to eight parameters.</span></div>
            <div className="guide-file"><code>@PatchState</code><span>Selected compatible values can be included in bounded saved state. Unmarked module values reset with the program.</span></div>
            <div className="guide-file"><code>Native changes</code><span>UI, permissions, new dependencies and new native functions require a new APK baseline.</span></div>
            <div className="guide-file"><code>Unsupported UI and migration</code><span>Arbitrary Android/Material Compose UI, drawing, platform APIs in common code and automatic database migration are outside the patch boundary.</span></div>
          </div>
          <p>The entire current supported screen program is delivered together, so clients can skip intermediate versions. The catalog routes by app, channel, installed base version and native fingerprint; a missing match means no update is offered.</p>
        </section>

        <section className="guide-section" id="github">
          <div className="eyebrow">Option A</div>
          <h2>Publish through GitHub Releases</h2>
          <ol className="guide-steps">
            <li>Set the APK catalog URL to <code>https://github.com/OWNER/REPO/releases/latest/download/catalog.json</code> before the APK build.</li>
            <li>Build and install that APK. Make supported Kotlin screen changes.</li>
            <li>Run <code>npx patchrelay patch --github</code>. CLI builds and signs the patch, then creates a GitHub Release.</li>
            <li>The release contains <code>screens.js</code>, <code>patch.json</code> and the updated <code>catalog.json</code>. New patch assets use that release’s unique tag URL; the app keeps checking the stable latest-catalog URL.</li>
          </ol>
          <div className="guide-note"><strong>Access requirement:</strong> The app downloads assets without GitHub credentials. The repository and its release assets must be publicly accessible. CLI publishing uses Git Credential Manager or <code>GITHUB_TOKEN</code>; that credential stays on the developer machine.</div>
        </section>

        <section className="guide-section" id="personal-server">
          <div className="eyebrow">Option B</div>
          <h2>Host files on your own server or CDN</h2>
          <p>During <code>init</code>, choose <code>https</code>. Set <code>patchBaseUrl</code> to the directory prefix for versioned patch assets. Set <code>catalogUrl</code> to one permanent catalog file per app and channel.</p>
          <pre className="guide-code"><code>{`patchBaseUrl = https://updates.example.com/myapp/patches
catalogUrl   = https://updates.example.com/myapp/production/catalog.json`}</code></pre>
          <p>Map those URLs to a persistent server directory. Example document root:</p>
          <pre className="guide-tree"><code>{`/srv/www/updates/myapp/
├── production/
│   └── catalog.json
└── patches/
    ├── <tag-1>/
    │   ├── screens.js
    │   └── patch.json
    ├── <tag-2>/
    │   ├── screens.js
    │   └── patch.json
    └── <tag-3>/
        ├── screens.js
        └── patch.json`}</code></pre>
          <p>Each tag is generated by PatchRelay and appears in the exact destinations printed after <code>npx patchrelay patch</code>. Use that tag exactly. Keep the same public catalog URL; don’t create a new catalog URL for every patch.</p>
          <div className="guide-file-list">
            {serverFiles.map(([path, detail]) => <div className="guide-file" key={path}><code>{path}</code><span>{detail}</span></div>)}
          </div>
        </section>

        <section className="guide-section" id="repeat">
          <div className="eyebrow">Every patch after setup</div>
          <h2>Upload in this order</h2>
          <ol className="guide-steps">
            <li>Run <code>npx patchrelay patch</code>. It creates fresh files under <code>screens/build/patchrelay/patches</code> and prints each remote URL.</li>
            <li>Create the new tag folder on the server. Upload <code>screens.js</code> and <code>patch.json</code> to their printed URLs first.</li>
            <li>Check that both patch URLs return the real file over HTTPS without login or an HTML error page.</li>
            <li>Upload the new <code>catalog.json</code> to the same stable <code>catalogUrl</code> last. Replace the catalog atomically where possible.</li>
          </ol>
          <div className="guide-note"><strong>Do not mirror the local output folder.</strong> PatchRelay cleans and reuses its local output directory for each run. Copy only the new release files into a new server tag folder. A mirror or sync command that deletes remote files can remove older patches. Keep any versioned folder still referenced by a catalog route unchanged.</div>
          <p>The signed catalog keeps the newest patch route for each supported installed APK base. Different apps and channels need separate catalog URLs. A native Android change needs a new APK; PatchRelay updates supported Kotlin screen code.</p>
        </section>

        <section className="guide-section" id="checklist">
          <div className="eyebrow">Before release</div>
          <h2>Check the setup</h2>
          <ul className="guide-checks">
            <li>APK has the intended stable <code>catalogUrl</code> and channel.</li>
            <li>Catalog, manifest and program URLs use HTTPS and are reachable by the app without developer credentials.</li>
            <li>Catalog URL returns signed <code>catalog.json</code>; versioned URLs return the exact <code>patch.json</code> and <code>screens.js</code> files.</li>
            <li>New patch files are hosted before the catalog is replaced.</li>
            <li>Private signing key stays private. Never upload it, APK baseline backups, or source archives.</li>
          </ul>
          <div className="guide-note"><strong>No update appeared?</strong> Check that the catalog URL and channel were embedded in the installed APK, the app can fetch all three public HTTPS files, and the catalog contains a route matching that APK’s app ID, base version and native fingerprint. Only supported Kotlin screen changes can be patched; native changes require a new APK.</div>
          <Link className="quiet-link guide-back" href="/">Return to setup and CLI commands ↑</Link>
        </section>
      </main>
    </>
  );
}
