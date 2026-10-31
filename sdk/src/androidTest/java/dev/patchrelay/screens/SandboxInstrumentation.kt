package dev.patchrelay.screens

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SandboxActivity : Activity() {
    lateinit var root: FrameLayout
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        root = FrameLayout(this)
        root.addView(android.widget.TextView(this).apply { text = "PatchRelay SDK security checks…" })
        setContentView(root)
    }
}

/** Runs the actual worker/CSP/timeout boundary on the installed Android WebView provider. */
class SandboxInstrumentation : Instrumentation() {
    private lateinit var activity: SandboxActivity
    private var backgroundFaultsOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        backgroundFaultsOnly = arguments?.getString("backgroundFaultsOnly") == "true"
        start()
    }
    override fun onStart() {
        val status = Bundle().apply {
            putString("class", this@SandboxInstrumentation.javaClass.name)
            putString("test", if (backgroundFaultsOnly) "storageAndHttpsFaults" else "sandboxAndRecovery")
            putInt("numtests", 1); putInt("current", 1)
        }
        sendStatus(1, status)
        try {
            if (backgroundFaultsOnly) {
                val baseline = context.assets.open("baseline-screen.js").bufferedReader().use { it.readText() }
                storageProbe(baseline)
                interruptedHttpsProbe()
                status.putString("stream", "PASS: background-only storage commit/write and real HTTPS interruption checks\n")
                sendStatus(0, status)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "OK (background fault tests)\n") })
                return
            }
            activity = startActivitySync(Intent(targetContext, SandboxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SandboxActivity
            val baseline = context.assets.open("baseline-screen.js").bufferedReader().use { it.readText() }
            probe(baseline, "renderScreen", listOf("checkout")) { check(it.contains("checkout")) }
            apiProbe()
            recoveryProbe(baseline)
            rendererCrashProbe(baseline)
            val exports = "self['PatchRelay:screens']={renderScreen:function(){return JSON.stringify(" +
                "['window','document','Android','java','Java','__relay','RTCPeerConnection','localStorage'].map(function(n){return typeof self[n]}));}};"
            probe(exports, "renderScreen", emptyList()) {
                val values = JSONArray(it)
                check(values.length() == 8 && (0 until 8).all { index -> values.getString(index) == "undefined" })
            }
            probe("self['PatchRelay:screens']={renderScreen:function(){try{indexedDB.open('escape');return 'allowed'}catch(e){return 'denied'}}};",
                "renderScreen", emptyList()) { check(it == "denied") }
            val network = "self.blocked=[];Promise.all([function(){return fetch('https://example.invalid/escape')}," +
                "function(){return fetch('file:///sdcard/escape')},function(){return fetch('content://settings/system')}," +
                "function(){return new WebSocket('wss://example.invalid')}]" +
                ".map(function(f){return Promise.resolve().then(f).then(function(){return false},function(){return true})}))" +
                ".then(function(v){self.blocked=v});" +
                "self['PatchRelay:screens']={renderScreen:function(){return JSON.stringify(self.blocked)}};"
            probe(network, "renderScreen", emptyList(), repeatUntil = { JSONArray(it).length() == 4 }) {
                val values = JSONArray(it)
                check((0 until 4).all { index -> values.getBoolean(index) })
            }
            probe("self['PatchRelay:screens']={renderScreen:function(){try{importScripts('https://example.invalid/x.js');return 'allowed'}catch(e){return 'denied'}}};",
                "renderScreen", emptyList()) { check(it == "denied") }
            probe("throw new Error('bad patch')", expectFailure = true)
            probe("while(true){}", expectFailure = true, timeout = true)
            // New interpreter must work immediately after terminating an infinite loop.
            probe(baseline, "renderScreen", listOf("checkout")) { check(it.contains("checkout")) }
            status.putString("stream", "PASS: native API, updater recovery/faults, sandbox restrictions, exception, timeout and replacement worker\n")
            sendStatus(0, status)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "OK (1 test)\n") })
        } catch (failure: Throwable) {
            status.putString("stack", android.util.Log.getStackTraceString(failure))
            sendStatus(-2, status)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "FAIL: $failure\n") })
        } finally {
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
        }
    }

    private fun storageProbe(bundled: String) {
        var failedCommits = 0
        val wrapped = object : android.content.ContextWrapper(targetContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val preferences = super.getSharedPreferences(name, mode)
                return object : android.content.SharedPreferences by preferences {
                    override fun edit(): android.content.SharedPreferences.Editor {
                        val edit = preferences.edit()
                        return object : android.content.SharedPreferences.Editor by edit {
                            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor { edit.putString(key, value); return this }
                            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { edit.putLong(key, value); return this }
                            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { edit.putInt(key, value); return this }
                            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor { edit.putBoolean(key, value); return this }
                            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { edit.putFloat(key, value); return this }
                            override fun putStringSet(key: String, value: Set<String>?): android.content.SharedPreferences.Editor { edit.putStringSet(key, value); return this }
                            override fun remove(key: String): android.content.SharedPreferences.Editor { edit.remove(key); return this }
                            override fun clear(): android.content.SharedPreferences.Editor { edit.clear(); return this }
                            override fun commit(): Boolean {
                                val result = edit.commit()
                                if (failedCommits > 0) { failedCommits--; return false }
                                return result
                            }
                        }
                    }
                }
            }
        }
        val keys = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = java.util.Base64.getEncoder().encodeToString(keys.public.encoded)
        val base = "storage-" + java.util.UUID.randomUUID()
        val host = "b".repeat(64)
        val fixture = mutableMapOf<String, ByteArray>()
        val channel = "https://test.invalid/manifest"
        val artifact = "https://test.invalid/script"
        val updater = ScreenUpdates(wrapped, base, setOf(publicKey), setOf("checkout"), host) { url, _ -> fixture.getValue(url) }
        fun offer(version: Int) {
            val bytes = bundled.toByteArray(Charsets.UTF_8)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val payload = org.json.JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
                .put("appId", targetContext.packageName).put("baseVersion", base).put("hostSha256", host).put("version", version)
                .put("screenIds", org.json.JSONArray().put("checkout")).put("sha256", hash).put("artifactUrl", artifact)
                .toString().toByteArray(Charsets.UTF_8)
            val signature = java.security.Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload) }.sign()
            fixture[channel] = org.json.JSONObject().put("payload", java.util.Base64.getEncoder().encodeToString(payload))
                .put("signature", java.util.Base64.getEncoder().encodeToString(signature)).toString().toByteArray(Charsets.UTF_8)
            fixture[artifact] = bytes
        }
        updater.start(bundled)
        offer(1); check(updater.stage(channel))
        val preferences = wrapped.getSharedPreferences("patchrelay-screens", 0)
        val saved = preferences.all.toMap()
        for (failureCount in listOf(1, 2)) {
            offer(2); failedCommits = failureCount
            try { updater.stage(channel); error("Accepted failed state commit") } catch (_: java.io.IOException) { }
            check(preferences.all == saved && updater.pendingVersion() == 1L) { "Failed commit changed selection/replay state" }
        }
        check(updater.stage(channel) && updater.pendingVersion() == 2L)
        val directory = java.io.File(wrapped.noBackupFilesDir, "screen-patches/" + java.util.UUID.nameUUIDFromBytes("$base:$host".toByteArray()))
        offer(3)
        check(directory.setWritable(false, true))
        try {
            try { updater.stage(channel); error("Accepted unwritable patch storage") } catch (_: java.io.IOException) { }
            check(updater.pendingVersion() == 2L)
        } finally { check(directory.setWritable(true, true)) { "Cannot restore test directory permissions" } }
        check(updater.stage(channel) && updater.pendingVersion() == 3L)
        sendStatus(2, Bundle().apply { putString("stream", "PASS: failed commit (including failed rollback report) preserves pending version; unwritable storage preserves selection; retries succeed\n") })
    }

    private fun interruptedHttpsProbe() {
        var received = 0
        var disconnected = false
        val address = "https://api.github.com/repos/JetBrains/kotlin"
        try {
            dev.patchrelay.core.HttpsDownloads.download(address, 65536) { url ->
                val real = url.openConnection() as java.net.HttpURLConnection
                real.instanceFollowRedirects = false
                object : java.net.HttpURLConnection(url) {
                    override fun connect() = real.connect()
                    override fun disconnect() { disconnected = true; real.disconnect() }
                    override fun usingProxy() = real.usingProxy()
                    override fun setConnectTimeout(value: Int) { real.connectTimeout = value }
                    override fun setReadTimeout(value: Int) { real.readTimeout = value }
                    override fun getResponseCode() = real.responseCode
                    override fun getHeaderField(name: String?) = real.getHeaderField(name)
                    override fun getContentLengthLong() = real.contentLengthLong
                    override fun getInputStream(): java.io.InputStream = object : java.io.FilterInputStream(real.inputStream) {
                        override fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)
                        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                            if (received >= 32) throw java.io.IOException("Injected mid-stream read failure")
                            return super.read(bytes, offset, minOf(length, 32 - received)).also { if (it > 0) received += it }
                        }
                    }
                }
            }
            error("Interrupted HTTPS read returned a successful artifact")
        } catch (_: java.io.IOException) { check(received == 32 && disconnected) { "No real partial HTTPS read observed" } }
        val complete = dev.patchrelay.core.HttpsDownloads.download(address, 65536)
        check(org.json.JSONObject(String(complete, Charsets.UTF_8)).getString("full_name") == "JetBrains/kotlin")
        sendStatus(2, Bundle().apply { putString("stream", "PASS: actual TLS response interrupted after 32 bytes, connection closed, subsequent full HTTPS request succeeds\n") })
    }

    private fun rendererCrashProbe(bundled: String) {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            sendStatus(2, Bundle().apply { putString("stream", "SKIP: renderer termination requires API 29+\n") })
            return
        }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        var terminated = false
        var interpreter: ScreenInterpreter? = null
        runOnMainSync {
            interpreter = ScreenInterpreter(activity) { error ->
                if (!terminated || error.message != "Screen interpreter stopped") failure = error
                interpreter!!.close()
                done.countDown()
            }
            activity.root.addView(interpreter!!.webView, FrameLayout.LayoutParams(1, 1))
            interpreter!!.start(bundled) {
                terminated = interpreter!!.webView.webViewRenderProcess?.terminate() == true
                if (!terminated) { failure = AssertionError("Renderer termination unavailable"); done.countDown() }
            }
        }
        try { check(done.await(20, TimeUnit.SECONDS)) { "Renderer death did not report failure" } }
        finally { runOnMainSync { interpreter?.close() } }
        failure?.let { throw it }
        probe(bundled, "renderScreen", listOf("checkout")) { check(it.contains("checkout")) }
        sendStatus(2, Bundle().apply { putString("stream", "PASS: actual WebView renderer termination reports failure; replacement interpreter executes\n") })
    }

    private fun recoveryProbe(bundled: String) {
        // Ephemeral test keys and package-private storage; no developer key or demo state is touched.
        val keys = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = java.util.Base64.getEncoder().encodeToString(keys.public.encoded)
        val base = "recovery-" + java.util.UUID.randomUUID()
        val host = "b".repeat(64)
        val channel = "https://test.invalid/patch.json"
        val artifact = "https://test.invalid/screens.js"
        val downloads = mutableMapOf<String, ByteArray>()
        var downloadCount = 0
        fun updater() = ScreenUpdates(targetContext, base, setOf(publicKey), setOf("checkout"), host) { url, limit ->
            downloadCount++
            (downloads[url] ?: throw java.io.IOException("Injected interrupted download")).also { require(it.size <= limit) }
        }
        fun offer(version: Int, script: String) {
            val bytes = script.toByteArray(Charsets.UTF_8)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val payload = org.json.JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
                .put("appId", targetContext.packageName).put("baseVersion", base).put("hostSha256", host)
                .put("version", version).put("screenIds", org.json.JSONArray().put("checkout"))
                .put("sha256", hash).put("artifactUrl", artifact).toString().toByteArray(Charsets.UTF_8)
            val signer = java.security.Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload) }
            downloads[channel] = org.json.JSONObject().put("payload", java.util.Base64.getEncoder().encodeToString(payload))
                .put("signature", java.util.Base64.getEncoder().encodeToString(signer.sign())).toString().toByteArray(Charsets.UTF_8)
            downloads[artifact] = bytes
        }
        val first = updater()
        check(first.start(bundled).version == 0L)
        offer(1, bundled)
        check(first.stage(channel))
        val healthy = updater()
        check(healthy.start(bundled).version == 1L)
        healthy.markHealthy()
        offer(2, "throw new Error('Injected bad patch')")
        check(healthy.stage(channel))
        val broken = updater()
        val program = broken.start(bundled)
        check(program.version == 2L)
        probe(program.script, expectFailure = true)
        val restored = broken.reject(bundled, IllegalStateException("Observed bad startup"))
        check(restored.version == 1L)
        probe(restored.script, "renderScreen", listOf("checkout")) { check(it.contains("checkout")) }
        broken.markHealthy()
        check(!broken.stage(channel))
        sendStatus(2, Bundle().apply { putString("stream", "PASS: signed bad patch rejected, previous program executes, replay blocked\n") })

        offer(3, bundled)
        downloads.remove(artifact)
        try { broken.stage(channel); error("Accepted partial download") } catch (_: java.io.IOException) { }
        check(broken.pendingVersion() == 0L && broken.activeVersion() == 1L)
        downloads[channel] = "{invalid".toByteArray()
        try { broken.stage(channel); error("Accepted corrupt manifest") }
        catch (_: org.json.JSONException) { } catch (_: IllegalArgumentException) { }
        check(broken.activeVersion() == 1L)
        offer(3, bundled)
        check(broken.stage(channel))
        val crash = updater()
        check(crash.start(bundled).version == 3L)
        // Simulates the next process reading an uncleared boot marker; does not kill the phone app.
        val reboot = updater()
        check(reboot.start(bundled).version == 1L)
        reboot.markHealthy()
        check(!reboot.stage(channel))
        sendStatus(2, Bundle().apply { putString("stream", "PASS: interrupted download/corrupt manifest preserve active code; unconfirmed boot falls back\n") })

        offer(4, bundled)
        check(reboot.stage(channel))
        val directory = java.io.File(targetContext.noBackupFilesDir, "screen-patches/" +
            java.util.UUID.nameUUIDFromBytes("$base:$host".toByteArray(Charsets.UTF_8)))
        java.io.File(directory, "4.js").writeText("tampered")
        val corrupt = updater()
        check(corrupt.start(bundled).version == 1L)
        corrupt.markHealthy()
        val fallback = corrupt.disable(bundled)
        check(fallback.version == 0L)
        val count = downloadCount
        check(!corrupt.stage(channel) && downloadCount == count)
        val disabled = updater()
        check(disabled.start(bundled).version == 0L)
        disabled.enable()
        check(!disabled.stage(channel))
        offer(5, bundled)
        check(disabled.stage(channel))
        sendStatus(2, Bundle().apply { putString("stream", "PASS: corrupt saved program falls back; disable persists without downloads; only newer patch resumes\n") })
    }

    private fun apiProbe() {
        // Identical SDK path for real HTTPS and denied unknown IDs; JS still has no networking bridge.
        for (allowed in listOf(true, false)) {
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            var calls = 0
            val nativeFailure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            var engine: ScreenEngine? = null
            val function = if (allowed) "github.repository.v1" else "android.reflect"
            val program = "var label='Loading';var requested=false;self['PatchRelay:screens']={" +
                "renderScreen:function(){return JSON.stringify({screenId:'checkout',type:'column',children:[{id:'result',type:'text',text:label}]})}," +
                "takeHostRequests:function(){if(requested)return '[]';requested=true;return JSON.stringify([{id:1,function:'$function',argument:'JetBrains/kotlin'}])}," +
                "handleHostResult:function(screen,id,result,error){if(error)throw new Error(error);label=result;return this.renderScreen()}};"
            runOnMainSync {
                engine = ScreenEngine(activity, "checkout", onTree = { tree ->
                    if (tree.children.single().text != "Loading") {
                        try {
                            check(allowed && calls == 1)
                            check(tree.children.single().text == "JetBrains/kotlin")
                        } catch (error: Throwable) { failure = error }
                        done.countDown()
                    }
                }, onFailure = { error ->
                    if (allowed || calls != 0 || error !is IllegalArgumentException) failure = nativeFailure.get() ?: error
                    done.countDown()
                })
                engine!!.registerHostFunction("github.repository.v1") { repository ->
                    check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
                    require(repository == "JetBrains/kotlin")
                    calls++
                    try {
                        val bytes = dev.patchrelay.core.HttpsDownloads.download("https://api.github.com/repos/$repository", 65536)
                        org.json.JSONObject(String(bytes, Charsets.UTF_8)).getString("full_name")
                    } catch (error: Exception) { nativeFailure.set(error); throw error }
                }
                activity.root.addView(engine!!.webView, FrameLayout.LayoutParams(1, 1))
                engine!!.start(program)
            }
            try { check(done.await(40, TimeUnit.SECONDS)) { "Host API operation did not complete" } }
            finally { runOnMainSync { engine?.close() } }
            failure?.let { throw it }
            check(calls == if (allowed) 1 else 0)
            sendStatus(2, Bundle().apply { putString("stream", if (allowed) "PASS: worker → SDK → Kotlin HTTPS → worker → native tree\n" else "PASS: unknown host operation rejected before native execution\n") })
        }
    }

    private fun probe(script: String, function: String? = null, args: List<String> = emptyList(),
        expectFailure: Boolean = false, timeout: Boolean = false, repeatUntil: (String) -> Boolean = { true },
        validate: (String) -> Unit = {}) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        var interpreter: ScreenInterpreter? = null
        sendStatus(2, Bundle().apply { putString("stream", "Probe: " + script.take(70) + "\n") })
        runOnMainSync {
            try {
                interpreter = ScreenInterpreter(activity) { error ->
                    if (!expectFailure || (timeout && error !is java.util.concurrent.TimeoutException)) failure = error
                    done.countDown()
                }
                activity.root.addView(interpreter!!.webView, FrameLayout.LayoutParams(1, 1))
                fun read() {
                    interpreter!!.call(function!!, args) { result ->
                        if (!repeatUntil(result)) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ read() }, 25)
                        else {
                            try { check(!expectFailure) { "Malicious program accepted" }; validate(result) }
                            catch (error: Throwable) { failure = error }
                            done.countDown()
                        }
                    }
                }
                interpreter!!.start(script) {
                    if (function != null) read()
                    else { failure = AssertionError("Malicious startup accepted"); done.countDown() }
                }
            } catch (error: Throwable) { failure = error; done.countDown() }
        }
        try { check(done.await(25, TimeUnit.SECONDS)) { "Interpreter failed to respond/recover" } }
        finally { runOnMainSync { interpreter?.close() } }
        failure?.let { throw it }
    }
}
