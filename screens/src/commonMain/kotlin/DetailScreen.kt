internal fun renderDetail(): List<String> = listOf(
    text("det_title", "Product Detail: $selectedItem", "title"),
    layout("det_specs_card", "card", listOf(
        text("det_heading", "Hardware Specifications & Reviews", "caption"),
        text("det_rating", "Customer Rating: 4.9 / 5.0 (142 Verified Reviews)"),
        text("det_p1", "Item: $selectedItem"),
        text("det_p2", "Current Unit Price: $$itemPrice USD"),
        text("det_p3", "Warehouse: In Stock (Express Dispatch within 2 Hours)")
    )),
    layout("det_addons_card", "card", listOf(
        text("addons_title", "Premium Upgrades", "caption"),
        toggle("toggle_warranty", "Include 2-Year Extended Care (+$29)", warrantyChecked, "toggle_warranty"),
        toggle("toggle_priority", "Include Priority Courier Delivery (+$15)", priorityShipping, "toggle_priority"),
        toggle("toggle_vip_ship", "VIP Autonomous Drone Dispatch (+$25)", vipExpressDelivery, "toggle_vip_ship")
    )),
    layout("det_qty_card", "card", listOf(
        text("qty_label", "Quantity Selected: $quantity units"),
        layout("qty_buttons", "row", listOf(
            button("qty_minus", "Remove (-1)", "remove", quantity > 1),
            button("qty_plus", "Add (+1)", "add", quantity < 100)
        ))
    )),
    layout("det_actions", "row", listOf(
        button("det_back", "Back to Catalog", "to_catalog"),
        button("det_checkout", "Proceed to Checkout ($${calculateTotal()})", "to_checkout")
    ))
)

