const setup = [
  {
    number: "01 / CLI",
    icon: "⌘",
    title: "Install the command line",
    copy: "From your Android project root, add PatchRelay as a development dependency. The package includes the CLI and its Gradle build plugin.",
    command: "npm install --save-dev @orbital_mcp/patchrelay",
  },
  {
    number: "02 / REQUIREMENTS",
    icon: "✓",
    title: "Check your build environment",
    copy: "Use Node 22+, JDK 17+ and Android SDK 35. The Gradle wrapper runs configured Kotlin/JS compile and Android tasks.",
    command: "npx patchrelay --help",
  },
  {
    number: "03 / APP SETUP",
    icon: "▤",
    title: "Prepare the native app",
    copy: "Creates patchrelay.config.json and asks for a patch-host base URL plus the app's stable catalog URL. Add the SDK and printed Gradle hooks; init does not create an APK.",
    command: "npx patchrelay init",
  },
  {
    number: "04 / ANDROID SDK",
    icon: "◇",
    title: "Add the published SDK",
    copy: "The Android library is available from Maven Central. Add mavenCentral() and this dependency to your prepared Android app.",
    command: 'implementation("io.github.jaypal1046:patchrelay-android:0.1.0-alpha01")',
  },
];

const workflow = [
  ["STEP 01", "Build the APK", "Release build records its native baseline and bundled fallback program."],
  ["STEP 02", "Edit supported Kotlin", "Change shared screens and supported logic; keep native inputs and function contracts compatible."],
  ["STEP 03", "Compile and sign", "Patch uses the current APK baseline, reserves the next version and creates a signed catalog."],
  ["STEP 04", "Host and activate", "Upload the generated files to the printed HTTPS destinations. App stages the update for next launch."],
];

const commands = [
  {
    number: "BUILD",
    icon: "＋",
    title: "Build APK + automatic baseline",
    copy: "With the PatchRelay Gradle hook applied, the release build creates the APK and saves its baseline record automatically. Keep the record private.",
    command: ".\\gradlew.bat :app:assembleRelease",
  },
  {
    number: "REVIEW",
    icon: "⌕",
    title: "See what changed",
    copy: "Compare configured native and screen inputs against a release baseline. Native changes require a new APK.",
    command: "npx patchrelay compare --baseline PATH\\baseline.json --diff",
  },
  {
    number: "RELEASE",
    icon: "↗",
    title: "Build a signed patch",
    copy: "Uses the current release baseline and next version from setup. Creates screens.js, patch.json and catalog.json under screens/build/patchrelay.",
    command: "npx patchrelay patch",
  },
];

function SectionHeading({ eyebrow, title, detail }: { eyebrow: string; title: string; detail: string }) {
  return (
    <div className="section-heading">
      <div><div className="eyebrow">{eyebrow}</div><h2>{title}</h2></div>
      <p>{detail}</p>
    </div>
  );
}

function CardGrid({ items }: { items: typeof setup }) {
  return (
    <div className="cards">
      {items.map((item) => (
        <article className="card" key={item.number}>
          <div className="card-top"><span className="number">{item.number}</span><span className="icon">{item.icon}</span></div>
          <h3>{item.title}</h3><p>{item.copy}</p>
          <code className="code">{item.command}</code>
        </article>
      ))}
    </div>
  );
}

export default function Home() {
  return (
    <>
      <header className="topbar shell">
        <a className="brand" href="#top"><span className="mark">↗</span> PatchRelay</a>
        <nav aria-label="Main navigation"><a href="#workflow">Workflow</a><a href="#hosting">Hosting</a><a href="/hosting">Full guide</a><a href="#artifacts">Files</a><a href="#install">Install</a></nav>
        <div className="top-right"><span className="badge">Android · Kotlin/JS</span><a className="quiet-link" href="https://central.sonatype.com/artifact/io.github.jaypal1046/patchrelay-android">Maven Central ↗</a></div>
      </header>
      <main className="shell" id="top">
        <section className="hero">
          <div>
            <div className="eyebrow">Signed updates for prepared Android apps</div>
            <h1>Ship screen updates without a new APK.</h1>
            <p>PatchRelay compiles supported shared Kotlin screens into a signed JavaScript update. Your Android app verifies and activates it using the PatchRelay SDK already installed in the APK.</p>
            <div className="actions"><a className="button" href="#install">Get started <span>→</span></a><a className="quiet-link" href="https://central.sonatype.com/artifact/io.github.jaypal1046/patchrelay-android">View SDK release ↗</a></div>
          </div>
          <div className="terminal" aria-label="Example PatchRelay commands">
            <div className="term-head"><span className="dots"><i /><i /><i /></span><span>PowerShell · project folder</span></div>
            <div className="term-line"><span className="comment"># install CLI in your project</span></div>
            <div className="term-line"><span className="prompt">PS&gt;</span> npm install --save-dev @orbital_mcp/patchrelay</div>
            <div className="term-line"><span className="comment"># after setup + the first release APK</span></div>
            <div className="term-line"><span className="prompt">PS&gt;</span> npx patchrelay patch</div>
            <div className="term-foot">Local compile · signed update · manual server or GitHub Release</div>
          </div>
        </section>

        <div className="stats">
          <div className="stat"><strong>Node.js 22+ · JDK 17+</strong><span>Android SDK 35 for Android builds</span></div>
          <div className="stat"><strong>SDK on Maven Central</strong><span>io.github.jaypal1046:patchrelay-android</span></div>
          <div className="stat"><strong>Two hosting options</strong><span>Personal HTTPS server or GitHub Releases</span></div>
        </div>

        <section className="section" id="install">
          <SectionHeading eyebrow="Start here" title="Install the tools, prepare the app" detail="CLI package and Android SDK are separate pieces." />
          <CardGrid items={setup} />
        </section>

        <section className="section" id="capabilities">
          <SectionHeading eyebrow="Compatibility" title="Know what a patch can change" detail="PatchRelay updates supported shared Kotlin screen code inside an app already integrated with the SDK." />
          <div className="cards capability-cards">
            <article className="card"><div className="number">PATCHABLE</div><h3>Shared screen code</h3><p>Common Kotlin screens, screen events, navigation and compatible logic are compiled into one complete update. Supported Compose widgets include Text, Button, TextField, Toggle, Column, Row and Card.</p></article>
            <article className="card"><div className="number">PATCHABLE</div><h3>Connected functions and chosen state</h3><p>Public top-level functions marked <code>@PatchFunction</code> can route supported scalar inputs/results. Values marked <code>@PatchState</code> can be saved across program updates.</p></article>
            <article className="card"><div className="number">APK REQUIRED</div><h3>Native app changes</h3><p>Native Android UI, permissions, dependencies, SDK hooks, and new host functions need an APK build. PatchRelay does not download or install APKs, DEX files, or native libraries.</p></article>
          </div>
          <p className="workflow-note"><strong>Important boundary:</strong> This is not a converter for arbitrary Android or Compose screens. Android Material UI, platform APIs in shared code, custom drawing, asynchronous Compose effects, automatic database migrations and unmarked application state are not patched. See the <a href="/hosting">full deployment guide</a> for install and hosting requirements.</p>
        </section>

        <section className="section" id="workflow">
          <SectionHeading eyebrow="The release path" title="From Kotlin edit to installed update" detail="Baseline once for each native app build." />
          <div className="workflow">
            {workflow.map(([step, title, copy], index) => (
              <article className="workflow-item" key={step}><small>{step}</small><h3>{title}</h3><p>{copy}</p>{index < workflow.length - 1 && <span className="arrow">→</span>}</article>
            ))}
          </div>
          <div className="deliver" id="artifacts">
            <div><div className="eyebrow">What each command creates</div><h2>Setup once. Patch from here.</h2><p><code>init</code> records the HTTPS destinations, baseline pointer and output folder. Build the first release APK once; its Gradle hook saves the baseline. Each <code>patch</code> run compiles the update, signs its manifest and catalog, and advances the version.</p></div>
            <div className="file-list">
              <div className="file"><code>init → patchrelay.config.json</code><span className="safe">Project settings</span></div>
              <div className="file"><code>init → .patchrelay-tools/</code><span className="safe">Commit build plugin</span></div>
              <div className="file"><code>init → local.properties + .gitignore</code><span className="private">Local settings / ignore rules</span></div>
              <div className="file"><code>assembleRelease → app-release.apk</code><span className="private">Local release build</span></div>
              <div className="file"><code>Gradle hook → baseline.json + screen-base.apk</code><span className="private">Private release record</span></div>
              <div className="file"><code>baseline-screen.js + baseline-sources.zip</code><span className="private">Keep in full backup</span></div>
              <div className="file"><code>screens/build/patchrelay/patches/screens.js</code><span className="safe">Program</span></div>
              <div className="file"><code>screens/build/patchrelay/patches/patch.json</code><span className="safe">Signed manifest</span></div>
              <div className="file"><code>screens/build/patchrelay/patches/catalog.json</code><span className="safe">Stable discovery file</span></div>
              <div className="file"><code>.keys/patch-signing.key</code><span className="private">Never share</span></div>
            </div>
          </div>
        </section>

        <section className="section commands-section">
          <SectionHeading eyebrow="Useful commands" title="Run from your app project root" detail="Complete setup and build the first APK once. Then run this after each Kotlin change." />
          <CardGrid items={commands} />
          <p className="workflow-note"><strong>Already configured?</strong> Don’t run <code>init</code> again. After the release APK has a current automatic baseline, <code>patch</code> selects it, chooses the next unused version and writes all three release files under <code>screens/build/patchrelay</code>. Standard <code>patch</code> does not upload; use <code>patch --github</code> for GitHub Releases. From the <code>screens</code> folder in this checkout, run <code>node ..\cli\bin\patchrelay.mjs patch</code>.</p>
        </section>

        <section className="section" id="hosting">
          <SectionHeading eyebrow="Hosting setup" title="Host updates without breaking discovery" detail="The APK downloads one stable catalog URL. That catalog points to a signed manifest, and the manifest points to the compiled screen program." />
          <div className="deliver">
            <div><h3>1. Choose permanent HTTPS addresses</h3><p>During <code>npx patchrelay init</code>, choose <strong>https</strong> for your own server. Enter the folder where versioned patches will live, then a permanent catalog file URL. Example settings:</p><p><code>patchBaseUrl = https://updates.example.com/myapp/patches</code></p><p><code>catalogUrl = https://updates.example.com/myapp/production/catalog.json</code></p><p>Put the catalog URL in the APK&apos;s native configuration before building the first APK. The installed app checks that address; changing it later requires a rebuilt and reinstalled APK.</p></div>
            <div className="file-list">
              <div className="file"><code>production/catalog.json</code><span className="safe">Same URL</span></div>
              <div className="file"><code>patches/&lt;tag-1&gt;/screens.js + patch.json</code><span className="safe">Keep folder</span></div>
              <div className="file"><code>patches/&lt;tag-2&gt;/screens.js + patch.json</code><span className="safe">New folder per patch</span></div>
              <div className="file"><code>patches/&lt;tag-3&gt;/screens.js + patch.json</code><span className="safe">Never overwrite</span></div>
            </div>
          </div>
          <p className="workflow-note"><strong>2. Repeat safely for every patch.</strong> Run <code>npx patchrelay patch</code>. Each run prints a new tag and exact HTTPS destinations. Copy <code>screens.js</code> and <code>patch.json</code> from <code>screens/build/patchrelay/patches</code> into that new tag folder. Verify both URLs download without login, then replace <code>catalog.json</code> at the same stable <code>catalogUrl</code>. The catalog holds the latest patch route for each installed APK base; it is updated in place after the new files are hosted. Keep old tag folders unchanged while referenced. Separate apps and channels need separate catalog URLs.</p>
          <p className="workflow-note"><strong>Important:</strong> The CLI cleans and reuses its local output folder on every run. Don’t sync the whole local folder to the server with delete/mirror enabled; that would remove previous patches. Upload only the two new files into the new tag folder, then upload the catalog.</p>
          <p className="workflow-note"><strong>GitHub Releases instead?</strong> Run <code>npx patchrelay patch --github</code>. It creates a release with the three assets and uses <code>https://github.com/OWNER/REPO/releases/latest/download/catalog.json</code> for the stable catalog URL. Releases must be publicly downloadable. Standard <code>patch</code> only builds files; personal-server uploads stay manual.</p>
          <p className="hosting-link"><a className="quiet-link" href="/hosting">Read the full hosting guide: GitHub setup, server folder structure, repeat uploads, and troubleshooting →</a></p>
        </section>

        <aside className="boundary"><span>ⓘ</span><p><strong>Know the boundary.</strong> This CLI is for prepared Kotlin/JS Android projects, not a converter for arbitrary Android apps. It does not install SDK hooks or migrate arbitrary app databases. Personal-server uploads are manual; <code>patch --github</code> publishes GitHub Release assets. Updates activate after app restart. The published SDK is available from <a href="https://central.sonatype.com/artifact/io.github.jaypal1046/patchrelay-android">Maven Central</a>. Manage publishing at the <a href="https://central.sonatype.com/publishing/deployments">Sonatype Central Portal</a>.</p></aside>
      </main>
      <footer className="footer shell"><span>PatchRelay · Experimental alpha</span><div><a href="https://central.sonatype.com/artifact/io.github.jaypal1046/patchrelay-android">Maven Central</a><a href="https://central.sonatype.com/publishing/deployments">Publishing deployments</a></div></footer>
    </>
  );
}
