// Shared Application & Navigation State across multiple files
internal var page = "dashboard"

// State: Catalog & Detail
internal var catalogSearch = ""
internal var catalogCategory = "all"
internal var selectedItem = "Orbital AI Controller Node (PRO)"
internal var itemPrice = 159
internal var warrantyChecked = false
internal var priorityShipping = false
internal var vipExpressDelivery = false

// State: Cart & Checkout
internal var quantity = 1
internal var email = ""
internal var address = ""
internal var promoInput = ""
internal var promoDiscount = 0
internal var promoMessage = "Enter 'PATCH50' for $50 instant discount."
internal var accepted = false
internal var confirmed = false
internal var orders = 1

// State: Profile & Settings
internal var profileName = "Developer Jay (VIP Gold)"
internal var profileEmail = "jaypal@developer.com"
internal var darkTheme = true
internal var patchAlerts = true
internal var telemetry = true
internal var biometricAuth = true
internal var profileStatus = "VIP Profile synchronized with on-device patch v3 (Multi-File)!"

// State: Orders
internal var trackingStatus = "Order #PR-9802: Priority Drone delivery dispatched from Whitefield Hub!"

// State: Support
internal var supportFeedback = ""
internal var ticketStatus = "24/7 Priority VIP Developer support channel active."

internal fun validEmail() = email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

internal fun calculateTotal(): Int {
    val base = quantity * itemPrice
    val warranty = if (warrantyChecked) 29 else 0
    val shipping = if (vipExpressDelivery) 25 else if (priorityShipping) 15 else 0
    return (base + warranty + shipping - promoDiscount).coerceAtLeast(0)
}

