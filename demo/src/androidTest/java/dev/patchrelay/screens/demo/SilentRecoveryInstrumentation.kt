package dev.patchrelay.screens.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import dev.patchrelay.screens.ScreenUpdates
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** CLI-driven checks against the real release Activity and pinned-key SDK staging. */
class SilentRecoveryInstrumentation : Instrumentation() {
    private var args: Bundle? = null
    override fun onCreate(arguments: Bundle?) { args = arguments; super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            waitForIdleSync()
            val app = targetContext.applicationContext as DemoApp
            app.prepared.get(30, TimeUnit.SECONDS)
            val action = args?.getString("action") ?: "generated_workflow"
            when (action) {
                "stage_real_patch" -> check(stageRealPatch(app)) { "Patch version was already accepted" }
                "reject_replay" -> {
                    check(!stageRealPatch(app)) { "Already accepted patch version was staged again" }
                    verifyPages(app, true)
                }
                "reject_bad_patch" -> {
                    var rejected = false
                    try { stageRealPatch(app) } catch (_: SecurityException) { rejected = true }
                    check(rejected && app.updates.pendingVersion() == 0L) { "Tampered patch was not rejected cleanly" }
                }
                "generated_workflow", "generated_restore" -> verifyPages(app, action == "generated_restore")
                else -> error("Unknown verification action")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "OK ($action)\n") })
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", android.util.Log.getStackTraceString(failure)) })
        }
    }

    private fun verifyPages(app: DemoApp, restoreOnly: Boolean) {
        val version = args?.getString("version")?.toLong() ?: 0L
        val patched = args?.getString("edition") == "patch"
        check(app.selected?.version == version) { "Expected active version $version; actual ${app.selected?.version}" }
        val edition = if (patched) "v2" else "v1"
        val price = if (patched) 9 else 12
        val points = if (patched) 20 else 10
        val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun go(page: String) {
            val label = when (page) { "home" -> if (patched) "Start" else "Home"; "catalog" -> if (patched) "Shop" else "Catalog"; "cart" -> if (patched) "Basket" else "Cart"; "profile" -> if (patched) "Account" else "Profile"; else -> "Offers" }
            runOnMainSync { views(activity).filterIsInstance<Button>().single { it.text.toString() == label }.let { if (it.isEnabled) it.performClick() } }
            await(activity) { "${page.replaceFirstChar { it.uppercaseChar() }} $edition" in it }
        }
        try {
            await(activity) { "Simple pages $edition" in it && it.any { line -> line.contains("Shared Kotlin shipping: 15") && line.contains("Shared Kotlin status: Pending") && line.contains("Shared reward points: ${2 * points}") } }
            if (restoreOnly || version > 0) {
                await(activity) { "Cart $edition" in it && "Items: 2" in it && "Total: $${2 * price}" in it && "Reward points: ${2 * points}" in it }
                if (restoreOnly) {
                    check(JSONObject(app.savedState!!).getJSONObject("values").getString("simple.name") == "Jay")
                    return
                }
                go("profile"); await(activity) { "Hello Jay" in it }
            }
            go("cart")
            var quantity = 0
            runOnMainSync { quantity = texts(activity).first { it.startsWith("Items: ") }.substringAfter(": ").toInt() }
            while (quantity > 0) { click(activity, "Remove one"); quantity--; await(activity) { "Items: $quantity" in it } }
            go("home")
            await(activity) { (if (patched) "Welcome back to the updated app" else "Welcome to your simple app") in it }
            click(activity, if (patched) "Explore catalog" else "Browse catalog")
            await(activity) { "Catalog $edition" in it && "Unit price: $$price" in it }
            click(activity, if (patched) "Add travel notebook" else "Add notebook")
            await(activity) { "Items in cart: 1" in it }
            go("cart")
            await(activity) { "Total: $$price" in it && "Reward points: $points" in it }
            click(activity, "Add one")
            await(activity) { "Total: $${2 * price}" in it && "Reward points: ${2 * points}" in it }
            go("profile")
            runOnMainSync { views(activity).filterIsInstance<EditText>().single().setText("Jay") }
            await(activity) { "Hello Jay" in it }
            await(activity) { app.savedState?.let { snapshot -> JSONObject(snapshot).getJSONObject("values").let { it.getString("simple.page") == "profile" && it.getString("simple.name") == "Jay" } } == true }
            if (patched) { go("offers"); await(activity) { "Free delivery on every order" in it } }
            go("cart")
            await(activity) {
                app.savedState?.let { snapshot -> JSONObject(snapshot).getJSONObject("values").let {
                    it.getInt("simple.quantity") == 2 && it.getString("simple.name") == "Jay" && it.getString("simple.page") == "cart"
                } } == true
            }
            val disk = JSONObject(app.stateStore.load()!!).getJSONObject("values")
            check(disk.getInt("simple.quantity") == 2 && disk.getString("simple.name") == "Jay")
        } finally { runOnMainSync { activity.finish() } }
    }

    /** Test-only USB byte transport; the SDK verifies its real pinned key, route, hash and replay state. */
    private fun stageRealPatch(app: DemoApp): Boolean {
        val folder = app.getExternalFilesDir(null) ?: error("App external files unavailable")
        val script = File(folder, "screens.js").readBytes()
        val manifest = File(folder, "patch.json").readBytes()
        val catalog = File(folder, "catalog.json").readBytes()
        val catalogEnvelope = JSONObject(String(catalog, Charsets.UTF_8))
        val catalogPayload = JSONObject(String(Base64.getDecoder().decode(catalogEnvelope.getString("payload")), Charsets.UTF_8))
        val routes = catalogPayload.getJSONArray("entries")
        val manifestUrl = (0 until routes.length()).map { routes.getJSONObject(it) }.single {
            it.getString("baseVersion") == BuildConfig.BASE_VERSION && it.getString("hostSha256") == BuildConfig.HOST_SHA256
        }.getString("manifestUrl")
        val envelope = JSONObject(String(manifest, Charsets.UTF_8))
        val payload = JSONObject(String(Base64.getDecoder().decode(envelope.getString("payload")), Charsets.UTF_8))
        val artifact = payload.getString("artifactUrl")
        val uri = java.net.URI(artifact)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null)
        val catalogUrl = "https://local-verification.invalid/catalog.json"
        val download: (String, Int) -> ByteArray = { url, limit ->
            val bytes = when (url) { catalogUrl -> catalog; manifestUrl -> manifest; artifact -> script; else -> error("Unexpected fixture URL") }
            require(bytes.size <= limit); bytes
        }
        val constructor = ScreenUpdates::class.java.declaredConstructors.single { it.parameterTypes.size == 6 }
        val importer = constructor.newInstance(app, BuildConfig.BASE_VERSION, setOf(BuildConfig.PATCH_PUBLIC_KEY), BuildConfig.SCREEN_IDS.split(',').toSet(), BuildConfig.HOST_SHA256, download) as ScreenUpdates
        return importer.stageCatalog(catalogUrl, BuildConfig.UPDATE_CHANNEL)
    }

    private fun views(activity: Activity): List<View> {
        fun descend(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
        return descend(activity.findViewById(android.R.id.content))
    }
    private fun texts(activity: Activity) = views(activity).filterIsInstance<TextView>().map { it.text.toString() }
    private fun await(activity: Activity, condition: (List<String>) -> Boolean) {
        var last = emptyList<String>()
        repeat(600) { var ready = false; runOnMainSync { last = texts(activity); ready = condition(last) }; if (ready) return; Thread.sleep(50) }
        error("Timed out waiting for simple pages: $last")
    }
    private fun click(activity: Activity, label: String) {
        runOnMainSync { check(views(activity).filterIsInstance<Button>().single { it.text.toString() == label }.performClick()) }
    }
}
