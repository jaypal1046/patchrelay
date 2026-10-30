@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

private var quantity = 1
private var status = "Payment ready."

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    val total = quantity * 100
    return column(screenId, listOf(
        text("Checkout", heading = true),
        text("Items: $quantity"),
        text("Total: $total"),
        text(status),
        button("Add item", "add"),
        button("Pay $total", "pay")
    ))
}

@JsExport
fun handleEvent(screenId: String, action: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        "add" -> { if (quantity < 100) quantity++; status = "Payment ready." }
        "pay" -> status = "Payment confirmed: ${quantity * 100}"
        else -> error("Unsupported action")
    }
    return renderScreen(screenId)
}
