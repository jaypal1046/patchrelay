@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

private var quantity = 1
private var email = ""
private var accepted = false
private var confirmed = false
private var orders = 0
private var page = "cart"
private fun validEmail() = email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
private fun summary() = layout("summary", "card", listOf(
    text("items", "Items: $quantity"), text("total", "Total: ${quantity * 100}")
))

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    val content = when (page) {
        "cart" -> listOf(
            summary(),
            layout("quantity", "row", listOf(
                button("remove", "Remove", "remove", quantity > 1 && !confirmed),
                button("add", "Add item", "add", quantity < 100 && !confirmed)
            )),
            button("next", "Continue to details", "to_details", !confirmed)
        ) + if (confirmed) listOf(button("new_order", "Start new order", "new_order")) else emptyList()
        "details" -> listOf(
            input("email", "Receipt email", email, "email"),
            text("validation", if (email.isEmpty()) "Enter an email for your receipt." else if (validEmail()) "Email ready." else "Enter a valid email.",
                if (email.isNotEmpty() && !validEmail()) "error" else "caption"),
            toggle("terms", "I agree to the order terms", accepted, "terms"),
            button("next", "Review order", "to_review", validEmail() && accepted),
            button("back", "Back to cart", "back_cart")
        )
        "review" -> listOf(
            summary(), text("receipt_email", "Receipt: $email"),
            button("pay", "Place demo order", "place_order"),
            button("back", "Back to details", "back_details")
        )
        "receipt" -> listOf(
            summary(), text("receipt_email", "Receipt: $email"),
            text("status", "Payment confirmed: ${quantity * 100}"),
            text("orders", "Orders created: $orders"),
            button("back", "Return to cart", "back_cart")
        )
        else -> error("Unknown page")
    }
    return column(screenId, listOf(
        text("title", when (page) { "cart" -> "Cart"; "details" -> "Details"; "review" -> "Review"; else -> "Receipt" }, "title"),
        text("step", "Cart → Details → Review → Receipt", "caption")
    ) + content, backAction = when (page) {
        "details", "receipt" -> "back_cart"
        "review" -> "back_details"
        else -> null
    })
}

@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        "add" -> if (page == "cart" && !confirmed && quantity < 100) quantity++
        "remove" -> if (page == "cart" && !confirmed && quantity > 1) quantity--
        "email" -> {
            require(value != null && value.length <= 1024)
            if (page == "details" && !confirmed) email = value
        }
        "terms" -> {
            require(value == "true" || value == "false")
            if (page == "details" && !confirmed) accepted = value == "true"
        }
        "to_details" -> if (page == "cart" && !confirmed) page = "details"
        "to_review" -> if (page == "details" && !confirmed && validEmail() && accepted) page = "review"
        "back_details" -> if (page == "review" && !confirmed) page = "details"
        "back_cart" -> if (page == "details" || page == "receipt") page = "cart"
        "place_order" -> if (page == "review" && !confirmed && validEmail() && accepted) {
            confirmed = true
            orders++
            page = "receipt"
        }
        "new_order" -> if (page == "cart" && confirmed) {
            quantity = 1; email = ""; accepted = false; confirmed = false
        }
        else -> error("Unsupported action")
    }
    return renderScreen(screenId)
}
