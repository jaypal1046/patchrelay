@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

internal var apiStatus = "Repository API has not been called."
internal var apiBusy = false
private var requestId = 0
private var pendingHostRequest = "[]"

internal fun requestRepository() {
    if (apiBusy) return
    check(requestId < Int.MAX_VALUE)
    requestId++
    apiBusy = true
    apiStatus = "Loading repository through Kotlin…"
    pendingHostRequest = "[{\"id\":$requestId,\"function\":\"github.repository.v1\",\"argument\":\"JetBrains/kotlin\"}]"
}

@JsExport
fun takeHostRequests(): String = pendingHostRequest.also { pendingHostRequest = "[]" }

@JsExport
fun handleHostResult(screenId: String, id: Int, result: String?, error: String?): String {
    require(screenId == "checkout")
    if (apiBusy && id == requestId) {
        apiBusy = false
        apiStatus = if (error != null) "API unavailable. Try again." else "Kotlin API result: ${result ?: "No result"}"
    }
    return renderScreen(screenId)
}
