internal fun navBar() = layout("nav_bar", "card", listOf(
    text("nav_title", "PatchRelay Multi-File Navigation Hub [v3 MODULAR]", "caption"),
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

