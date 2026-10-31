internal fun renderDashboard(): List<String> = listOf(
    layout("dash_hero", "card", listOf(
        text("dash_title", "PatchRelay PRO v3 [MULTI-FILE PATCH]", "title"),
        text("dash_banner", "DYNAMIC MODULAR ARCHITECTURE: 8 Screens split across 10 Kotlin files!", "caption"),
        text("dash_health", "System Health: 100% Operational · Architecture: Modular Multi-File · Hot Patching: ACTIVE", "caption"),
        text("dash_device", "Connected Phone: Realme RMX2117 | Multi-Screen Engine: Verified", "caption")
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

