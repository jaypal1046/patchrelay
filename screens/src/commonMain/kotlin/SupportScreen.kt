internal fun renderSupport(): List<String> = listOf(
    text("sup_title", "24/7 VIP Help & Developer Support [v3 Multi-File]", "title"),
    layout("faq_card1", "card", listOf(
        text("faq_q1", "Q: Can Kotlin Multiplatform split screens across multiple files?", "caption"),
        text("faq_a1", "A: YES! Kotlin/JS compiles all .kt files in commonMain into a single unified JavaScript bundle that PatchRelay signs and delivers.")
    )),
    layout("faq_card2", "card", listOf(
        text("faq_q2", "Q: Can patches modify native Android code or permissions?", "caption"),
        text("faq_a2", "A: No. Native files are cryptographically locked in baseline.json. The native contract cannot be altered by patches.")
    )),
    layout("faq_card3", "card", listOf(
        text("faq_q3", "Q: What is the typical patch size with 10+ Kotlin files?", "caption"),
        text("faq_a3", "A: About ~130 KB! Dead-code elimination in production builds keeps it ultra-lean.")
    )),
    layout("faq_card4", "card", listOf(
        text("faq_q4", "Q: How do events dispatch across files?", "caption"),
        text("faq_a4", "A: Through standard Kotlin internal functions and mutable state shared across the package.")
    )),
    layout("sup_form_card", "card", listOf(
        text("sup_form_head", "Priority Ticket Submission", "caption"),
        input("sup_input", "Enter your feedback or bug report", supportFeedback, "support_input"),
        text("sup_status", ticketStatus, "caption"),
        button("sup_submit", "Send Support Ticket", "submit_ticket", supportFeedback.isNotEmpty())
    )),
    button("sup_back", "Back to Dashboard", "to_dashboard")
)

