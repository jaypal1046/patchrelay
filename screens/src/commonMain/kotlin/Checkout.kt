@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

private var quantity = 1
private var email = ""
private var accepted = false
private var confirmed = false
private fun validEmail() = email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    val total = quantity * 100
    return column(screenId, listOf(
        text("title", "Checkout", "title"),
        text("subtitle", "Native widgets · Kotlin screen program", "caption"),
        layout("summary", "card", listOf(
            text("items", "Items: $quantity"),
            text("total", "Total: $total"),
            layout("quantity", "row", listOf(
                button("remove", "Remove", "remove", quantity > 1 && !confirmed),
                button("add", "Add item", "add", quantity < 100 && !confirmed)
            ))
        )),
        input("email", "Receipt email", email, "email"),
        text("validation", if (email.isEmpty()) "Enter an email for your receipt." else if (validEmail()) "Email ready." else "Enter a valid email.", if (email.isNotEmpty() && !validEmail()) "error" else "caption"),
        toggle("terms", "I agree to the order terms", accepted, "terms"),
        button("pay", if (confirmed) "Order confirmed" else "Pay $total", "pay", validEmail() && accepted && !confirmed),
        text("status", if (confirmed) "Payment confirmed: $total" else "Demo checkout — no real payment.", "caption")
    ))
}

@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        "add" -> if (!confirmed && quantity < 100) quantity++
        "remove" -> if (!confirmed && quantity > 1) quantity--
        "email" -> { require(value != null && value.length <= 1024); email = value }
        "terms" -> { require(value == "true" || value == "false"); accepted = value == "true" }
        "pay" -> if (validEmail() && accepted) confirmed = true
        else -> error("Unsupported action")
    }
    return renderScreen(screenId)
}
