package dev.patchrelay.screens.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.TextView
import dev.patchrelay.screens.ScreenUpdates
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Tests the real release Activity; fixture patches use ephemeral keys and isolated artifact paths. */
class SilentRecoveryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        try { runChecks() }
        catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", android.util.Log.getStackTraceString(failure)) })
        }
    }

    private fun runChecks() {
        waitForIdleSync()
        val app = targetContext.applicationContext as DemoApp
        app.prepared.get(15, java.util.concurrent.TimeUnit.SECONDS)
        val originalUpdates = app.updates
        val originalSelection = app.selected
        val prefs = app.getSharedPreferences("patchrelay-screens", 0)
        val saved = prefs.all.toMap()
        val base = "silent-ui-test-" + UUID.randomUUID()
        val host = "b".repeat(64)
        val directory = File(app.noBackupFilesDir, "screen-patches/" + UUID.nameUUIDFromBytes("$base:$host".toByteArray()))
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
        val candidate = "self['PatchRelay:screens']={" +
            "renderScreen:function(){return JSON.stringify({screenId:'checkout',type:'column',children:[{id:'candidate',type:'text',text:'REJECTED CANDIDATE'}]})}," +
            "takeHostRequests:function(){return '[]'},patchedFunctionIds:function(){return '[\"unregistered.native.v1\"]'}};"
        val runtimeFailure = "self['PatchRelay:screens']={" +
            "renderScreen:function(){return JSON.stringify({screenId:'checkout',type:'column',children:[{id:'title',type:'text',text:'Checkout'},{id:'next',type:'button',text:'Continue',action:'fail_now'}]})}," +
            "takeHostRequests:function(){return '[]'},patchedFunctionIds:function(){return '[]'}," +
            "handleEvent:function(){throw new Error('PRIVATE PATCH FAILURE')}};"
        val validCandidate = candidate.replace("[\"unregistered.native.v1\"]", "[]")
        check(!validCandidate.contains("unregistered.native.v1"))
        val transportProbe = "var exposed=false;for(var owner=self;owner;owner=Object.getPrototypeOf(owner)){" +
            "for(var name of ['postMessage','Worker','SharedWorker','MessageChannel','fetch','WebSocket','importScripts']){" +
            "var d=Object.getOwnPropertyDescriptor(owner,name);if(d&&(d.get||typeof d.value==='function'))exposed=true}};" +
            "if(!exposed)throw new Error('Unsafe transport denied');" +
            validCandidate
        val oversized = "self['PatchRelay:screens']={renderScreen:function(){return 'x'.repeat(8*1024*1024)},takeHostRequests:function(){return '[]'}};"
        val forged = "self.dispatchEvent(new MessageEvent('message',{data:{ticket:1,source:'while(true){}'}}));" + runtimeFailure
        val asynchronous = runtimeFailure.replace("renderScreen:function(){", "renderScreen:function(){setTimeout(function(){throw new Error('ASYNC FAILURE')},1000);")
        val approvedApi = "var requested=false;var reply='Loading repository';self['PatchRelay:screens']={" +
            "renderScreen:function(){return JSON.stringify({screenId:'checkout',type:'column',children:[{id:'title',type:'text',text:'Checkout'},{id:'api',type:'text',text:reply},{id:'next',type:'button',text:'Continue',action:'fail_now'}]})}," +
            "takeHostRequests:function(){if(requested)return '[]';requested=true;return JSON.stringify([{id:1,function:'github.repository.v1',argument:'JetBrains/kotlin'}])}," +
            "handleHostResult:function(screen,id,result,error){if(error)throw new Error(error);reply=result;return this.renderScreen()}," +
            "patchedFunctionIds:function(){return '[]'},handleEvent:function(){throw new Error('PRIVATE PATCH FAILURE')}};"
        val cases = listOf("startupFailure" to "throw new Error('PRIVATE PATCH FAILURE')", "hiddenCandidate" to candidate,
            "runtimeFailure" to runtimeFailure, "transportBlocked" to transportProbe, "oversizedResult" to oversized,
            "forgedRequest" to forged, "asyncFailure" to asynchronous, "approvedApi" to approvedApi)
        var activity: Activity? = null
        var failed = false
        var currentStatus = Bundle()
        try {
            for ((index, entry) in cases.withIndex()) {
                currentStatus = Bundle().apply {
                    putString("class", this@SilentRecoveryInstrumentation.javaClass.name)
                    putString("test", entry.first); putInt("numtests", cases.size); putInt("current", index + 1)
                }
                sendStatus(1, currentStatus)
                val updater = ScreenUpdates(app, base, publicKey, setOf("checkout"), host)
                val version = index + 1
                val bytes = entry.second.toByteArray(Charsets.UTF_8)
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                val payload = JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
                    .put("appId", app.packageName).put("baseVersion", base).put("hostSha256", host).put("version", version)
                    .put("screenIds", JSONArray().put("checkout")).put("sha256", hash)
                    .put("artifactUrl", "https://fixture.invalid/screens.js").toString().toByteArray(Charsets.UTF_8)
                val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload) }.sign()
                File(directory, "$version.js").writeBytes(bytes)
                File(directory, "$version.json").writeText(JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
                    .put("signature", Base64.getEncoder().encodeToString(signature)).toString())
                check(prefs.edit().putLong("pending", version.toLong()).putLong("highest", version.toLong()).commit())
                app.updates = updater
                app.selected = updater.start(app.bundled)
                check(app.selected!!.version == version.toLong())
                activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val hostActivity = activity!!
                val forbidden = AtomicReference<String>()
                val listener = ViewTreeObserver.OnPreDrawListener {
                    val text = texts(hostActivity.findViewById(android.R.id.content)).joinToString("\n")
                    if (listOf("REJECTED CANDIDATE", "PRIVATE PATCH FAILURE", "Patch failed", "Recovery failed", "Screen unavailable")
                            .any { it in text }) forbidden.compareAndSet(null, text)
                    true
                }
                runOnMainSync { hostActivity.window.decorView.viewTreeObserver.addOnPreDrawListener(listener) }
                try {
                    if (entry.first == "approvedApi") {
                        await(hostActivity) { text -> text.any { it.contains("JetBrains/kotlin") } }
                    }
                    if (entry.first in setOf("runtimeFailure", "forgedRequest", "asyncFailure", "approvedApi")) {
                        await(hostActivity) { text -> text.any { it == "Continue" } || (app.selected?.version == 0L && "Pay 100" in text) }
                        if (app.selected?.version == version.toLong()) {
                            if (entry.first == "asyncFailure") Thread.sleep(1200)
                            click(hostActivity, "Continue")
                        } else check(entry.first == "asyncFailure") { "Program failed before its requested action" }
                    }
                    await(hostActivity) { text -> app.selected?.version == 0L && text.any { it == "Pay 100" } }
                    click(hostActivity, "Add item")
                    await(hostActivity) { it.any { text -> text == "Pay 200" } }
                    click(hostActivity, "Pay 200")
                    await(hostActivity) { it.any { text -> text == "Payment confirmed: 200" } }
                    check(forbidden.get() == null) { "Exposed patch diagnostic/candidate: ${forbidden.get()}" }
                    currentStatus.putString("stream", "PASS ${entry.first}: silent bundled fallback; Add item and Pay still work\n")
                    sendStatus(0, currentStatus)
                } finally {
                    runOnMainSync {
                        hostActivity.window.decorView.viewTreeObserver.removeOnPreDrawListener(listener)
                        hostActivity.finish()
                    }
                    waitForIdleSync()
                    activity = null
                }
            }
        } catch (failure: Throwable) {
            failed = true
            currentStatus.putString("stack", android.util.Log.getStackTraceString(failure))
            sendStatus(-2, currentStatus)
        } finally {
            activity?.let { current -> runOnMainSync { current.finish() } }
            val editor = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> { @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>) }
            }
            check(editor.commit()) { "Cannot restore SDK state after test" }
            app.updates = originalUpdates
            app.selected = originalSelection
            for (version in 1..cases.size) {
                File(directory, "$version.js").delete()
                File(directory, "$version.json").delete()
            }
            directory.delete()
        }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", if (failed) "FAIL: silent recovery\n" else "OK (${cases.size} silent-recovery tests)\n") })
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun await(activity: Activity, condition: (List<String>) -> Boolean) {
        var last = emptyList<String>()
        repeat(600) {
            var ready = false
            runOnMainSync { last = texts(activity.findViewById(android.R.id.content)); ready = condition(last) }
            if (ready) return
            Thread.sleep(25)
        }
        error("Timed out waiting for working checkout: $last")
    }
    private fun click(activity: Activity, label: String) {
        fun find(view: View): Button? {
            if (view is Button && view.text.toString() == label && view.isEnabled) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        runOnMainSync { check(find(activity.findViewById(android.R.id.content))?.performClick() == true) { "Missing button: $label" } }
    }
}
