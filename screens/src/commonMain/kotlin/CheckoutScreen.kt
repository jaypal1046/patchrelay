internal fun renderCheckout(): List<String> = if (!confirmed) listOf(
    text("chk_title", "Checkout & Instant Discount [v3 Multi-File]", "title"),
    layout("chk_summary", "card", listOf(
        text("sum_title", "Order Breakdown & Loyalty Rewards", "caption"),
        text("sum_item", "Item: $selectedItem (x$quantity)"),
        text("sum_sub", "Subtotal: $${quantity * itemPrice}"),
        if (warrantyChecked) text("sum_war", "Extended Care: +$29") else text("sum_war_no", "Warranty: Standard", "caption"),
        if (vipExpressDelivery) text("sum_drone", "VIP Drone Shipping: +$25") else if (priorityShipping) text("sum_ship", "Priority Shipping: +$15") else text("sum_ship_no", "Standard Shipping: Free", "caption"),
        if (promoDiscount > 0) text("sum_disc", "PROMO 'PATCH50' APPLIED: -$promoDiscount USD") else text("sum_disc_no", "Promo: None", "caption"),
        text("sum_tax", "Estimated GST / Sales Tax: $0.00 (Zero-Tax Promotion)"),
        text("sum_total", "Grand Total: $${calculateTotal()}", "title")
    )),
    layout("chk_promo_card", "card", listOf(
        text("promo_title", "Promo / Voucher Code", "caption"),
        input("promo_input", "Enter coupon code (try PATCH50)", promoInput, "promo_input"),
        text("promo_msg", promoMessage, "caption"),
        button("promo_btn", "Apply Promo Code", "apply_promo")
    )),
    layout("chk_form", "card", listOf(
        text("form_title", "Contact & Delivery Destination", "caption"),
        input("chk_email", "Receipt email address", email, "email"),
        text("chk_email_val", if (email.isEmpty()) "Receipt email is required." else if (validEmail()) "Email valid." else "Invalid email address format.",
            if (email.isNotEmpty() && !validEmail()) "error" else "caption"),
        input("chk_address", "Full shipping delivery address", address, "address"),
        toggle("chk_terms", "I accept the checkout & warranty conditions", accepted, "terms")
    )),
    layout("chk_actions", "row", listOf(
        button("chk_pay", "Confirm & Pay ($${calculateTotal()})", "place_order", validEmail() && accepted && address.isNotEmpty()),
        button("chk_back", "Back to Home", "to_dashboard")
    ))
) else listOf(
    layout("rec_card", "card", listOf(
        text("rec_title", "Payment Success! [PATCHED MULTI-FILE FLOW]", "title"),
        text("rec_id", "Order ID: #PR-VIP-" + (orders * 31)),
        text("rec_items", "Ordered: $quantity x $selectedItem"),
        text("rec_amount", "Final Paid: $${calculateTotal()} USD"),
        text("rec_dest", "Invoice dispatched to: $email"),
        text("rec_status", "Drone dispatch in progress to: $address", "caption")
    )),
    layout("rec_btns", "row", listOf(
        button("rec_new", "Order More Items", "new_order"),
        button("rec_orders", "Track Live Package", "to_orders")
    ))
)

