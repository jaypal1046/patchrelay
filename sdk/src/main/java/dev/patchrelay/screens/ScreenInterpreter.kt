package dev.patchrelay.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import org.json.JSONObject
import org.json.JSONTokener

/** One local interpreter shared by screen events and prepared function calls. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class ScreenInterpreter(context: Context, private val onFailure: (Exception) -> Unit) {
    val webView = WebView(context)
    private val page = context.assets.open("patchrelay-interpreter.html").bufferedReader().use { it.readText() }
    private val handler = Handler(Looper.getMainLooper())
    private val requests = java.util.ArrayDeque<Pair<String, (String) -> Unit>>()
    private var closed = false
    private var disposed = false
    private var started = false
    private var busy = false
    private var sequence = 0
    private var timeout: Runnable? = null
    private var poll: Runnable? = null

    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        webView.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webView.alpha = 0f
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            databaseEnabled = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
                callback.invoke(origin, false, false)
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<android.net.Uri>>, params: FileChooserParams): Boolean {
                callback.onReceiveValue(null)
                return true
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel(); return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel(); return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                result.cancel(); return true
            }
        }
        webView.setDownloadListener { _, _, _, _, _ -> }
    }

    fun start(script: String, onReady: () -> Unit) {
        check(!started && !closed)
        started = true
        armTimeout()
        webView.webViewClient = object : WebViewClient() {
            private var initialized = false
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
            override fun onPageFinished(view: WebView, url: String) {
                if (initialized || closed) return
                initialized = true
                enqueue(script + "\n;" + wrap("'ready'")) { onReady() }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) fail(IllegalStateException("Screen interpreter page failed"))
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail(IllegalStateException("Screen interpreter stopped"))
                return true
            }
        }
        // An opaque origin also denies IndexedDB/other origin-backed storage in the worker.
        webView.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
    }

    fun call(function: String, args: List<Any?>, onResult: (String) -> Unit) {
        require(function in setOf("renderScreen", "handleEvent", "patchedFunctionIds", "invokePatch"))
        val arguments = args.joinToString(",") {
            when (it) { null -> "null"; is Int -> it.toString(); is String -> JSONObject.quote(it); else -> error("Unsupported argument") }
        }
        enqueue(wrap("self['PatchRelay:screens'].$function($arguments)"), onResult)
    }

    private fun wrap(expression: String) =
        "(function(){try{return JSON.stringify({ok:true,value:$expression});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()"

    private fun enqueue(source: String, onResult: (String) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return
        if (requests.size >= 64) { fail(IllegalStateException("Too many pending interpreter calls")); return }
        requests.addLast(source to onResult)
        pump()
    }

    private fun pump() {
        if (busy || closed || requests.isEmpty()) return
        busy = true
        val (source, onResult) = requests.removeFirst()
        val ticket = armTimeout()
        try {
            webView.evaluateJavascript("window.__relay.send($ticket,${JSONObject.quote(source)})") {
                if (!closed && ticket == sequence) readReply(ticket, onResult)
            }
        } catch (failure: Exception) { fail(failure) }
    }

    private fun readReply(ticket: Int, onResult: (String) -> Unit) {
        try { webView.evaluateJavascript("window.__relay.take($ticket)") { encoded ->
            if (closed || ticket != sequence) return@evaluateJavascript
            if (encoded == "null") {
                poll = Runnable { if (!closed && ticket == sequence) readReply(ticket, onResult) }
                handler.postDelayed(poll!!, 16)
                return@evaluateJavascript
            }
            timeout?.let(handler::removeCallbacks)
            try {
                require(encoded != null && encoded.length <= 128 * 1024) { "Invalid interpreter response" }
                val result = BoundedJson.objectValue(JSONTokener(encoded).nextValue() as? String ?: error("Program failed to load"), 128 * 1024)
                check(result.getBoolean("ok")) { result.optString("error", "Execution failed") }
                val value = result.get("value") as? String ?: error("Expected string result")
                busy = false
                onResult(value)
                pump()
            } catch (failure: Exception) { fail(failure) }
        } } catch (failure: Exception) { fail(failure) }
    }

    private fun armTimeout(): Int {
        timeout?.let(handler::removeCallbacks)
        val ticket = ++sequence
        timeout = Runnable { if (!closed && sequence == ticket) fail(java.util.concurrent.TimeoutException("Screen execution timed out")) }
        handler.postDelayed(timeout!!, 10000)
        return ticket
    }

    private fun fail(failure: Exception) {
        if (closed) return
        closed = true
        requests.clear()
        timeout?.let(handler::removeCallbacks)
        poll?.let(handler::removeCallbacks)
        stopWorker()
        onFailure(failure)
    }

    fun close() {
        if (disposed) return
        disposed = true
        closed = true
        requests.clear()
        timeout?.let(handler::removeCallbacks)
        poll?.let(handler::removeCallbacks)
        stopWorker()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun stopWorker() {
        // A renderer crash may make evaluateJavascript throw; recovery must still run.
        try { webView.evaluateJavascript("window.__relay && window.__relay.stop()", null) }
        catch (_: Exception) { }
    }
}
