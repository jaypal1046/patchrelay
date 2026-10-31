@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

// Navigation state
private var page = "dashboard"

// State: Catalog & Detail
private var catalogSearch = ""
private var catalogCategory = "all"
private var selectedItem = "Orbital AI Controller Node (PRO)"
private var itemPrice = 159
private var warrantyChecked = false
private var priorityShipping = false
private var vipExpressDelivery = false

// State: Cart & Checkout
private var quantity = 1
private var email = ""
private var address = ""
private var promoInput = ""
private var promoDiscount = 0
private var promoMessage = "Enter 'PATCH50' for $50 instant discount."
private var accepted = false
private var confirmed = false
private var orders = 1

// State: Profile & Settings
private var profileName = "Developer Jay (VIP Gold)"
private var profileEmail = "jaypal@developer.com"
private var darkTheme = true
private var patchAlerts = true
private var telemetry = true
private var biometricAuth = true
private var profileStatus = "VIP Profile synchronized with on-device patch v1!"

// State: Orders
private var trackingStatus = "Order #PR-9802: Priority Drone delivery dispatched from Whitefield Hub!"

// State: Support
private var supportFeedback = ""
private var ticketStatus = "24/7 Priority VIP Developer support channel active."

private fun validEmail() = email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

private fun calculateTotal(): Int {
    val base = quantity * itemPrice
    val warranty = if (warrantyChecked) 29 else 0
    val shipping = if (vipExpressDelivery) 25 else if (priorityShipping) 15 else 0
    return (base + warranty + shipping - promoDiscount).coerceAtLeast(0)
}

private fun navBar() = layout("nav_bar", "card", listOf(
    text("nav_title", "PatchRelay Navigation Hub [v1 HOT-PATCHED]", "caption"),
    layout("nav_row1", "row", listOf(
        button("nav_home", "Dashboard", "to_dashboard", page != "dashboard"),
        button("nav_cat", "Catalog (Sale!)", "to_catalog", page != "catalog"),
        button("nav_det", "Detail (Specs)", "to_detail", page != "detail")
    )),
    layout("nav_row2", "row", listOf(
        button("nav_chk", "Checkout ($${calculateTotal()})", "to_checkout", page != "checkout"),
        button("nav_api", "Live API", "to_api", page != "api_explorer"),
        button("nav_prf", "Profile", "to_profile", page != "profile")
    )),
    layout("nav_row3", "row", listOf(
        button("nav_ord", "Orders", "to_orders", page != "orders"),
        button("nav_sup", "VIP Support", "to_support", page != "support")
    ))
))

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen" }
    
    val content = when (page) {
        "dashboard" -> listOf(
            layout("dash_hero", "card", listOf(
                text("dash_title", "PatchRelay PRO v2 [PATCHED]", "title"),
                text("dash_banner", "DYNAMIC PATCH ACTIVE: Running 8 upgraded pages on-device!", "caption"),
                text("dash_health", "System Health: 99.99% Operational · Latency: 4ms · Memory: Bounded", "caption"),
                text("dash_device", "Connected Phone: Realme RMX2117 | Patch Engine: Verified", "caption")
            )),
            navBar(),
            layout("dash_stats", "card", listOf(
                text("stats_title", "Live Real-Time Telemetry", "title"),
                text("stat_orders", "Orders Logged: $orders | Active Cart: $quantity ($selectedItem)"),
                text("stat_vip", "VIP Gold Perks: 20% Storewide Discount Active"),
                text("stat_theme", "Dark Mode: Active | Biometrics: Enabled"),
                text("stat_api", "Host Bridge: $apiStatus", "caption")
            )),
            layout("dash_actions", "row", listOf(
                button("go_shop", "Shop Sale Catalog", "to_catalog"),
                button("go_api", "Test Host API", "to_api")
            ))
        )

        "catalog" -> listOf(
            text("cat_title", "Exclusive Hardware Catalog (4 Items) [v2]", "title"),
            text("cat_desc", "Special hot-patched holiday discounts applied automatically. In-stock guaranteed.", "caption"),
            layout("cat_filter_row", "row", listOf(
                button("cat_all", "All Items", "cat_filter_all", catalogCategory != "all"),
                button("cat_ctrl", "Controllers", "cat_filter_controllers", catalogCategory != "controllers"),
                button("cat_sensors", "Sensors", "cat_filter_sensors", catalogCategory != "sensors")
            )),
            if (catalogCategory == "all" || catalogCategory == "controllers") layout("item1", "card", listOf(
                text("item1_name", "[BEST SELLER] Orbital AI Controller Node", "title"),
                text("item1_price", "Sale Price: $159 USD (Was $199)", "caption"),
                text("item1_desc", "ARM64 neural-compute module with sub-millisecond dispatch."),
                layout("item1_btns", "row", listOf(
                    button("item1_sel", "View Full Specs", "select_item1"),
                    button("item1_add", "Quick Buy ($159)", "add_item1")
                ))
            )) else layout("empty1", "row", emptyList()),
            if (catalogCategory == "all" || catalogCategory == "controllers") layout("item2", "card", listOf(
                text("item2_name", "[POPULAR] Shizuku Wireless Bridge", "title"),
                text("item2_price", "Sale Price: $69 USD (Was $79)", "caption"),
                text("item2_desc", "High-speed ADB-free privileged IPC bridge."),
                layout("item2_btns", "row", listOf(
                    button("item2_sel", "View Full Specs", "select_item2"),
                    button("item2_add", "Quick Buy ($69)", "add_item2")
                ))
            )) else layout("empty2", "row", emptyList()),
            if (catalogCategory == "all" || catalogCategory == "sensors") layout("item3", "card", listOf(
                text("item3_name", "[NEW] Telemetry Sensor Module", "title"),
                text("item3_price", "Sale Price: $99 USD (Was $119)", "caption"),
                text("item3_desc", "Ultra-low power hardware diagnostic bus sensor."),
                layout("item3_btns", "row", listOf(
                    button("item3_sel", "View Full Specs", "select_item3"),
                    button("item3_add", "Quick Buy ($99)", "add_item3")
                ))
            )) else layout("empty3", "row", emptyList()),
            if (catalogCategory == "all" || catalogCategory == "sensors") layout("item4", "card", listOf(
                text("item4_name", "[HOT-PATCH BONUS] Quantum Sync Dongle", "title"),
                text("item4_price", "Price: $49 USD", "caption"),
                text("item4_desc", "Compact USB-C real-time OTA state sync transceiver."),
                layout("item4_btns", "row", listOf(
                    button("item4_sel", "View Full Specs", "select_item4"),
                    button("item4_add", "Quick Buy ($49)", "add_item4")
                ))
            )) else layout("empty4", "row", emptyList()),
            layout("cat_footer", "row", listOf(
                button("cat_back", "Back to Home", "to_dashboard"),
                button("cat_checkout", "Checkout ($${calculateTotal()})", "to_checkout")
            ))
        )

        "detail" -> listOf(
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

        "checkout" -> if (!confirmed) listOf(
            text("chk_title", "Checkout & Instant Discount [v2]", "title"),
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
                text("rec_title", "Payment Success! [PATCHED FLOW]", "title"),
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

        "api_explorer" -> listOf(
            text("api_page_title", "Live Native API Bridge [v2 PRO]", "title"),
            text("api_desc", "Interpreted JS calls asynchronous native Android host operation over bounded IPC channel.", "caption"),
            layout("api_card", "card", listOf(
                text("api_target", "Target: JetBrains/kotlin on GitHub API", "caption"),
                text("api_live_status", apiStatus),
                text("api_metrics", "Channel Latency: <15ms · Cache Mode: Validated · Protocol: TLS 1.3", "caption"),
                button("api_fetch_btn", "Query Live GitHub Stats", "load_repository", !apiBusy)
            )),
            layout("api_info_card", "card", listOf(
                text("info_heading", "Security & Sandbox Hardening", "caption"),
                text("info_p1", "Bounded download size limit: 65,536 bytes"),
                text("info_p2", "TLS Certificate validation enforced by Android platform"),
                text("info_p3", "No arbitrary socket or native execution allowed from patch scripts.")
            )),
            button("api_back", "Back to Dashboard", "to_dashboard")
        )

        "profile" -> listOf(
            text("prof_title", "VIP User Profile & Security", "title"),
            layout("prof_inputs_card", "card", listOf(
                text("prof_head", "Member Identity", "caption"),
                input("prof_name_input", "Full Name", profileName, "profile_name"),
                input("prof_email_input", "Account Email", profileEmail, "profile_email")
            )),
            layout("prof_settings_card", "card", listOf(
                text("settings_head", "Security & System Toggles", "caption"),
                toggle("prof_toggle_dark", "Dark Mode UI Theme", darkTheme, "toggle_dark"),
                toggle("prof_toggle_alerts", "Instant OTA Patch Push Alerts", patchAlerts, "toggle_alerts"),
                toggle("prof_toggle_telem", "Telemetry & Latency Diagnostics", telemetry, "toggle_telemetry"),
                toggle("prof_toggle_bio", "Require Biometric Fingerprint for Orders", biometricAuth, "toggle_biometrics")
            )),
            text("prof_feedback", profileStatus, "caption"),
            layout("prof_btns", "row", listOf(
                button("prof_save", "Update Profile Settings", "save_profile"),
                button("prof_back", "Back to Dashboard", "to_dashboard")
            ))
        )

        "orders" -> listOf(
            text("ord_title", "Order History & Real-Time Tracking", "title"),
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

        "support" -> listOf(
            text("sup_title", "24/7 VIP Help & Developer Support [v2]", "title"),
            layout("faq_card1", "card", listOf(
                text("faq_q1", "Q: How do patches update without installing APKs?", "caption"),
                text("faq_a1", "A: PatchRelay compiles Kotlin screen logic into signed JavaScript executed inside a sandboxed native WebView interpreter.")
            )),
            layout("faq_card2", "card", listOf(
                text("faq_q2", "Q: Can patches modify native Android code or permissions?", "caption"),
                text("faq_a2", "A: No. Native files are cryptographically locked in baseline.json. The native contract cannot be altered by patches.")
            )),
            layout("faq_card3", "card", listOf(
                text("faq_q3", "Q: What is the typical patch size?", "caption"),
                text("faq_a3", "A: Less than 120 KB, compared to a full 20-50 MB APK update!")
            )),
            layout("faq_card4", "card", listOf(
                text("faq_q4", "Q: How many git commits and iterations can be patched?", "caption"),
                text("faq_a4", "A: Unlimited! As long as native contracts remain invariant, any number of sequential commits can be patched.")
            )),
            layout("sup_form_card", "card", listOf(
                text("sup_form_head", "Priority Ticket Submission", "caption"),
                input("sup_input", "Enter your feedback or bug report", supportFeedback, "support_input"),
                text("sup_status", ticketStatus, "caption"),
                button("sup_submit", "Send Support Ticket", "submit_ticket", supportFeedback.isNotEmpty())
            )),
            button("sup_back", "Back to Dashboard", "to_dashboard")
        )

        else -> error("Unknown page: $page")
    }

    return column(screenId, content, backAction = if (page == "dashboard") null else "to_dashboard")
}

@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen" }
    when (action) {
        // Navigation actions
        "to_dashboard" -> page = "dashboard"
        "to_catalog" -> page = "catalog"
        "to_detail" -> page = "detail"
        "to_checkout" -> page = "checkout"
        "to_api" -> page = "api_explorer"
        "to_profile" -> page = "profile"
        "to_orders" -> page = "orders"
        "to_support" -> page = "support"

        // Catalog actions
        "cat_filter_all" -> catalogCategory = "all"
        "cat_filter_controllers" -> catalogCategory = "controllers"
        "cat_filter_sensors" -> catalogCategory = "sensors"
        "select_item1" -> { selectedItem = "Orbital AI Controller Node (PRO)"; itemPrice = 159; page = "detail" }
        "select_item2" -> { selectedItem = "Shizuku Wireless Bridge"; itemPrice = 69; page = "detail" }
        "select_item3" -> { selectedItem = "Telemetry Sensor Module"; itemPrice = 99; page = "detail" }
        "select_item4" -> { selectedItem = "Quantum Sync Dongle"; itemPrice = 49; page = "detail" }
        "add_item1" -> { selectedItem = "Orbital AI Controller Node (PRO)"; itemPrice = 159; quantity = 1; page = "checkout" }
        "add_item2" -> { selectedItem = "Shizuku Wireless Bridge"; itemPrice = 69; quantity = 1; page = "checkout" }
        "add_item3" -> { selectedItem = "Telemetry Sensor Module"; itemPrice = 99; quantity = 1; page = "checkout" }
        "add_item4" -> { selectedItem = "Quantum Sync Dongle"; itemPrice = 49; quantity = 1; page = "checkout" }

        // Detail actions
        "toggle_warranty" -> {
            require(value == "true" || value == "false")
            warrantyChecked = value == "true"
        }
        "toggle_priority" -> {
            require(value == "true" || value == "false")
            priorityShipping = value == "true"
        }
        "toggle_vip_ship" -> {
            require(value == "true" || value == "false")
            vipExpressDelivery = value == "true"
        }
        "add" -> if (quantity < 100) quantity++
        "remove" -> if (quantity > 1) quantity--

        // Checkout actions
        "promo_input" -> {
            require(value != null && value.length <= 1024)
            promoInput = value
        }
        "apply_promo" -> {
            if (promoInput.trim().equals("PATCH50", ignoreCase = true)) {
                promoDiscount = 50
                promoMessage = "Success! $50 VIP discount voucher applied!"
            } else {
                promoDiscount = 0
                promoMessage = "Invalid code. Try entering 'PATCH50'."
            }
        }
        "email" -> {
            require(value != null && value.length <= 1024)
            email = value
        }
        "address" -> {
            require(value != null && value.length <= 1024)
            address = value
        }
        "terms" -> {
            require(value == "true" || value == "false")
            accepted = value == "true"
        }
        "place_order" -> if (validEmail() && accepted && address.isNotEmpty()) {
            confirmed = true
            orders++
        }
        "new_order" -> {
            confirmed = false
            quantity = 1
            warrantyChecked = false
            priorityShipping = false
            vipExpressDelivery = false
            promoDiscount = 0
            page = "catalog"
        }

        // API actions
        "load_repository" -> requestRepository("JetBrains/kotlin")

        // Profile actions
        "profile_name" -> {
            require(value != null && value.length <= 1024)
            profileName = value
        }
        "profile_email" -> {
            require(value != null && value.length <= 1024)
            profileEmail = value
        }
        "toggle_dark" -> {
            require(value == "true" || value == "false")
            darkTheme = value == "true"
        }
        "toggle_alerts" -> {
            require(value == "true" || value == "false")
            patchAlerts = value == "true"
        }
        "toggle_telemetry" -> {
            require(value == "true" || value == "false")
            telemetry = value == "true"
        }
        "toggle_biometrics" -> {
            require(value == "true" || value == "false")
            biometricAuth = value == "true"
        }
        "save_profile" -> profileStatus = "Saved: $profileName (Biometrics: ${if (biometricAuth) "On" else "Off"})"

        // Orders actions
        "refresh_tracking" -> trackingStatus = "Drone GPS Ping: Altitude 120m, 1.4km from destination!"

        // Support actions
        "support_input" -> {
            require(value != null && value.length <= 1024)
            supportFeedback = value
        }
        "submit_ticket" -> {
            ticketStatus = "Priority VIP Ticket #PR-" + (3000 + orders) + " logged! Engineer assigned."
            supportFeedback = ""
        }

        else -> error("Unsupported action: $action")
    }
    return renderScreen(screenId)
}
