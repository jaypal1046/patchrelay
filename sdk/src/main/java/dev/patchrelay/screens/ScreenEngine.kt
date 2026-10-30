package dev.patchrelay.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import org.json.JSONObject
import org.json.JSONTokener

/** Hidden local JS execution; no JavaScript interface, file access or network navigation. */
@SuppressLint("SetJavaScriptEnabled")
class ScreenEngine(context: Context, private val screenId: String,
    private val onTree: (ScreenTree) -> Unit, private val onFailure: (Exception) -> Unit) {
    val webView = WebView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var disposed = false
    private var busy = true
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

    fun start(script: String) {
        armTimeout()
        webView.webViewClient = object : WebViewClient() {
            private var initialized = false
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
            override fun onPageFinished(view: WebView, url: String) {
                if (initialized || closed) return
                initialized = true
                evaluate(script + "\n;" + call("renderScreen", listOf(screenId)))
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail(IllegalStateException("Screen interpreter stopped"))
                return true
            }
        }
        webView.loadDataWithBaseURL("https://patchrelay.invalid/", "<!doctype html><meta http-equiv=Content-Security-Policy content=\"default-src 'none'; script-src 'unsafe-eval'\"><body></body>", "text/html", "UTF-8", null)
    }

    fun event(action: String): Boolean {
        if (busy || closed) return false
        busy = true
        evaluate(call("handleEvent", listOf(screenId, action)))
        return true
    }

    private fun call(function: String, args: List<String>): String {
        val arguments = args.joinToString(",") { JSONObject.quote(it) }
        return "(function(){try{return JSON.stringify({ok:true,tree:window['PatchRelay:screens'].$function($arguments)});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()"
    }

    private fun evaluate(source: String) {
        val ticket = armTimeout()
        webView.evaluateJavascript(source) { encoded ->
            if (closed || ticket != sequence) return@evaluateJavascript
            timeout?.let(handler::removeCallbacks)
            try {
                require(encoded != null && encoded.length <= 128 * 1024) { "Invalid interpreter response" }
                val result = JSONObject(JSONTokener(encoded).nextValue() as? String ?: error("Screen program failed to load"))
                check(result.getBoolean("ok")) { result.optString("error", "Screen execution failed") }
                val tree = ScreenTrees.parse(result.getString("tree"), screenId)
                busy = false
                onTree(tree)
            } catch (failure: Exception) { fail(failure) }
        }
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
        timeout?.let(handler::removeCallbacks)
        onFailure(failure)
    }

    fun close() {
        if (disposed) return
        disposed = true
        closed = true
        timeout?.let(handler::removeCallbacks)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
