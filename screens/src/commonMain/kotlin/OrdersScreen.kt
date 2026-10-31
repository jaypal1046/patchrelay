internal fun renderOrders(): List<String> = listOf(
    text("ord_title", "Order History & Real-Time Tracking [v3 Multi-File]", "title"),
    layout("ord_active_card", "card", listOf(
        text("ord_active_h", "Active Order Live Tracking", "caption"),
        text("ord_active_desc", trackingStatus),
        text("ord_active_step", "Stage: Dispatched -> Out for Delivery -> Expected: 35 mins", "caption"),
        button("ord_refresh", "Ping Courier GPS Status", "refresh_tracking")
    )),
    layout("ord_past_card", "card", listOf(
        text("ord_past_h", "Historical Completed Orders", "caption"),
        text("ord_item1", "Order #PR-8817: 1x Orbital Controller - $159 [Delivered]"),
        text("ord_item2", "Order #PR-8704: 2x Shizuku Bridge - $138 [Delivered]"),
        text("ord_item3", "Order #PR-8610: 1x Quantum Sync Dongle - $49 [Delivered]")
    )),
    layout("ord_nav_row", "row", listOf(
        button("ord_home", "Back to Dashboard", "to_dashboard"),
        button("ord_shop", "Shop More Items", "to_catalog")
    ))
)

