@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

private var quantity = 1
private var status = "Payment button is missing in this release."

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    val total = 0 // Baseline bug: the total ignores quantity.
    return column(screenId, listOf(
        text("Checkout", heading = true),
        text("Items: $quantity"),
        text("Total: $total"),
        text(status),
        button("Add item", "add")
        // Baseline bug: no payment button.
    ))
}

@JsExport
fun handleEvent(screenId: String, action: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        "add" -> if (quantity < 100) quantity++
        else -> error("Unsupported action")
    }
    return renderScreen(screenId)
}
