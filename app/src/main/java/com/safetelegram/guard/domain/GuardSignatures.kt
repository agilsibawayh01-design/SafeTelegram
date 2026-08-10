package com.safetelegram.guard.domain

/**
 * All text/content-description signatures used to recognise Telegram screens.
 *
 * Kept in one file (Single Responsibility: "what strings mean what") so that
 * if Telegram changes its UI wording, only this file needs updating — no
 * detector logic changes required.
 *
 * NOTE: we intentionally do NOT match on Android resource-id (viewIdResourceName)
 * anymore. Resource-id matching required the `flagReportViewIds` accessibility
 * flag, which widens what our service is allowed to introspect and is one of
 * the capability flags some banking-app protection SDKs specifically scan
 * for. Text/content-description signatures are sufficient for this app's
 * job and let us drop that flag entirely (see accessibility_service_config.xml).
 */
object GuardSignatures {

    /** Telegram's chat-list search entry point exposes this as its a11y label. */
    val SEARCH_ENTRY_CONTENT_DESCRIPTIONS = listOf("search", "cari")

    /**
     * Telegram shows this section header ONLY when search results include
     * public/global entities (channels, groups, bots, users not already in
     * your chats). Local, in-chat search never shows these headers — that's
     * exactly the distinction FR-06/07/08 require us to preserve.
     */
    val GLOBAL_RESULT_SECTION_HEADERS = listOf(
        "global search",
        "pencarian global"
    )
}
