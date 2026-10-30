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
internal class ScreenInterpreter(context: Context, private val onFailure: (Exception) -> Unit) {
    val webView = WebView(context)
    private val handler = Handler(Looper.getMainLooper())
    private val requests = java.util.ArrayDeque<Pair<String, (String) -> Unit>>()
    private var closed = false
    private var disposed = false
    private var started = false
    private var busy = false
    private var sequence = 0
    private var timeout: Runnable? = null

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
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    fun start(script: String, onReady: () -> Unit) {
        check(!started && !closed)
        started = true
        armTimeout()
        webView.webViewClient = object : WebViewClient() {
            private var initialized = false
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
            override fun onPageFinished(view: WebView, url: String) {
                if (initialized || closed) return
                initialized = true
                enqueue(script + "\n;" + wrap("'ready'")) { onReady() }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail(IllegalStateException("Screen interpreter stopped"))
                return true
            }
        }
        webView.loadDataWithBaseURL("https://patchrelay.invalid/", "<!doctype html><meta http-equiv=Content-Security-Policy content=\"default-src 'none'; script-src 'unsafe-eval'\"><body></body>", "text/html", "UTF-8", null)
    }

    fun call(function: String, args: List<Any?>, onResult: (String) -> Unit) {
        require(function in setOf("renderScreen", "handleEvent", "patchedFunctionIds", "invokePatch"))
        val arguments = args.joinToString(",") {
            when (it) { null -> "null"; is Int -> it.toString(); is String -> JSONObject.quote(it); else -> error("Unsupported argument") }
        }
        enqueue(wrap("window['PatchRelay:screens'].$function($arguments)"), onResult)
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
        try { webView.evaluateJavascript(source) { encoded ->
            if (closed || ticket != sequence) return@evaluateJavascript
            timeout?.let(handler::removeCallbacks)
            try {
                require(encoded != null && encoded.length <= 128 * 1024) { "Invalid interpreter response" }
                val result = JSONObject(JSONTokener(encoded).nextValue() as? String ?: error("Program failed to load"))
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
        onFailure(failure)
    }

    fun close() {
        if (disposed) return
        disposed = true
        closed = true
        requests.clear()
        timeout?.let(handler::removeCallbacks)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
