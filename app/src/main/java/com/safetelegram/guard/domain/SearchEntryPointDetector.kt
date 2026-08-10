package com.safetelegram.guard.domain

/**
 * Decides whether a single node (the source of a click event) looks like the
 * top-level Search entry point on Telegram's chat-list screen, as opposed to
 * a search field inside an already-open chat.
 */
fun interface SearchEntryPointDetector {
    fun isSearchEntryPoint(node: ScreenNode): Boolean
}

class ContentDescriptionSearchEntryPointDetector(
    private val signatures: List<String> = GuardSignatures.SEARCH_ENTRY_CONTENT_DESCRIPTIONS
) : SearchEntryPointDetector {

    override fun isSearchEntryPoint(node: ScreenNode): Boolean {
        val desc = node.contentDescription?.trim()?.lowercase() ?: return false
        return signatures.any { desc == it || desc.contains(it) }
    }
}
