@file:OptIn(kotlin.js.ExperimentalJsExport::class)

import kotlin.js.JsExport

@JsExport
fun renderScreen(screenId: String): String {
    require(screenId == "checkout") { "Unknown screen: $screenId" }

    val content = when (page) {
        "dashboard" -> renderDashboard()
        "catalog" -> renderCatalog()
        "detail" -> renderDetail()
        "checkout" -> renderCheckout()
        "api_explorer" -> renderApiExplorer()
        "profile" -> renderProfile()
        "orders" -> renderOrders()
        "support" -> renderSupport()
        else -> error("Unknown page: $page")
    }

    return column(screenId, content, backAction = if (page == "dashboard") null else "to_dashboard")
}

@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String {
    require(screenId == "checkout") { "Unknown screen: $screenId" }
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
