internal fun renderProfile(): List<String> = listOf(
    text("prof_title", "VIP User Profile & Security [v3 Multi-File]", "title"),
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

