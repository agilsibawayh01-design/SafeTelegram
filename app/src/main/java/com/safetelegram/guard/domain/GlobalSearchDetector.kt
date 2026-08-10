package com.safetelegram.guard.domain

/**
 * Decides whether the currently visible screen shows Telegram's Global
 * Search results. Pure function of a [ScreenNode] tree — no Android, no I/O,
 * no side effects, trivially testable.
 */
fun interface GlobalSearchDetector {
    fun isGlobalSearchVisible(root: ScreenNode): Boolean
}

/**
 * Walks the visible node tree looking for a Global Search section header.
 *
 * Depth is capped defensively — Telegram's tree is normally < 15 levels
 * deep; 25 is a safety margin against pathological trees without letting a
 * malformed tree turn this into unbounded work on the main thread.
 */
class TextSignatureGlobalSearchDetector(
    private val signatures: List<String> = GuardSignatures.GLOBAL_RESULT_SECTION_HEADERS,
    private val maxDepth: Int = 25
) : GlobalSearchDetector {

    override fun isGlobalSearchVisible(root: ScreenNode): Boolean =
        matches(root, depth = 0)

    private fun matches(node: ScreenNode, depth: Int): Boolean {
        if (depth > maxDepth) return false

        val text = node.text?.trim()?.lowercase()
        val desc = node.contentDescription?.trim()?.lowercase()
        if (text != null && signatures.any { it == text }) return true
        if (desc != null && signatures.any { it == desc }) return true

        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            if (matches(child, depth + 1)) return true
        }
        return false
    }
}
