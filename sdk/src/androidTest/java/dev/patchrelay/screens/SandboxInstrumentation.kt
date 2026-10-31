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
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val status = Bundle().apply {
            putString("class", this@SandboxInstrumentation.javaClass.name); putString("test", "sandboxAndRecovery")
            putInt("numtests", 1); putInt("current", 1)
        }
        sendStatus(1, status)
        try {
            activity = startActivitySync(Intent(targetContext, SandboxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SandboxActivity
            val baseline = context.assets.open("baseline-screen.js").bufferedReader().use { it.readText() }
            probe(baseline, "renderScreen", listOf("checkout")) { check(it.contains("checkout")) }
            val exports = "self['PatchRelay:screens']={renderScreen:function(){return JSON.stringify(" +
                "['window','document','Android','java','Java','__relay','RTCPeerConnection','localStorage'].map(function(n){return typeof self[n]}));}};"
            probe(exports, "renderScreen", emptyList()) {
                val values = JSONArray(it)
                check(values.length() == 8 && (0 until 8).all { index -> values.getString(index) == "undefined" })
            }
            probe("self['PatchRelay:screens']={renderScreen:function(){try{indexedDB.open('escape');return 'allowed'}catch(e){return 'denied'}}};",
                "renderScreen", emptyList()) { check(it == "denied") }
            val network = "self.blocked=[];Promise.all([fetch('https://example.invalid/escape')," +
                "fetch('file:///sdcard/escape'),fetch('content://settings/system')," +
                "new Promise(function(resolve,reject){try{var s=new WebSocket('wss://example.invalid');s.onopen=resolve;s.onerror=reject}catch(e){reject(e)}})]" +
                ".map(function(p){return p.then(function(){return false},function(){return true})}))" +
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
            status.putString("stream", "PASS: compiled program, missing native/DOM bridges, storage/network restrictions, exception, timeout and replacement worker\n")
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
