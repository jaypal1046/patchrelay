internal fun renderCatalog(): List<String> = listOf(
    text("cat_title", "Exclusive Hardware Catalog (4 Items) [v3 Multi-File]", "title"),
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

