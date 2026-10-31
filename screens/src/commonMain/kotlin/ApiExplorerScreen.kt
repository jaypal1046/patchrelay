internal fun renderApiExplorer(): List<String> = listOf(
    text("api_page_title", "Live Native API Bridge [v3 Multi-File]", "title"),
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

