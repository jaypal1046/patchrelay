@file:OptIn(kotlin.js.ExperimentalJsExport::class)
import kotlin.js.JsExport

private var quantity = 1
private var status = "Payment ready."

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    return column(screenId, listOf(
        text("title", "Checkout", "title"),
        text("items", "Items: $quantity"),
        text("total", "Total: ${quantity * 100}"),
        text("status", status),
        button("add", "Add item", "add"),
        button("pay", "Pay ${quantity * 100}", "pay")
    ))
}

@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        "add" -> if (quantity < 100) quantity++
        "pay" -> status = "Payment confirmed: ${quantity * 100}"
        else -> error("Unsupported action")
    }
    return renderScreen(screenId)
}
