package com.safetelegram.guard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Safe Telegram — TelegramGuardService
 *
 * Strategy
 * --------
 * Android's AccessibilityService API cannot literally "consume" a touch event
 * inside another app (it can only consume key events and its own gestures),
 * so we cannot physically prevent the tap on the Search icon from being
 * delivered to Telegram. Instead we use the approach every reputable
 * "distraction blocker" accessibility app uses:
 *
 *   1. Detect the moment Global Search opens (by scanning the visible node
 *      tree for signatures that only appear in that screen).
 *   2. Immediately call performGlobalAction(GLOBAL_ACTION_BACK) to bounce the
 *      user back to the chat list, before they can read/tap anything.
 *   3. Do this with no dialog, no toast, no sound (Silent Mode requirement).
 *
 * Because Telegram's internal resource-id names can change between app
 * versions/forks, detection is deliberately layered: we first try known
 * resource-id patterns, then fall back to text-based signatures (section
 * headers Telegram shows above global results). If Telegram changes its UI
 * and detection stops matching, update SEARCH_TRIGGER_IDS /
 * GLOBAL_RESULT_SIGNATURES below — no other code changes should be needed.
 */
class TelegramGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastBackAt = 0L

    companion object {
        private const val TAG = "SafeTelegramGuard"

        val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web"
        )

        // Resource-id fragments seen on the chat-list search bar / search
        // fragment in stock Telegram for Android. Matched with "contains"
        // since Telegram appends dynamic suffixes to some ids.
        val SEARCH_ENTRY_ID_HINTS = listOf(
            "search_edit_text",
            "chats_search",
            "action_bar_search"
        )

        // Section headers Telegram shows ONLY when results include public
        // (global) entities — i.e. the thing we must block. Local search
        // inside a single chat never shows these headers.
        val GLOBAL_RESULT_SIGNATURES = listOf(
            "global search",
            "pencarian global",
            "public channel",
            "channel publik",
            "global"
        )

        // Debounce so we don't spam GLOBAL_ACTION_BACK if several
        // accessibility events fire for the same UI change.
        const val BACK_DEBOUNCE_MS = 400L

        // Small delay before re-checking after a raw click on the search
        // icon, to give Telegram's fragment transaction time to render.
        const val POST_CLICK_CHECK_DELAY_MS = 180L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Safe Telegram guard connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TELEGRAM_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handlePossibleSearchTap(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> checkForGlobalSearchNow()
        }
    }

    /**
     * When the user taps the search icon/field on the chat-list screen, react
     * fast: schedule a check shortly after, once Telegram has rendered the
     * search fragment. Tapping search WHILE INSIDE a chat is left alone —
     * that's FR-06/07/08 (in-chat search must keep working) — we only react
     * when the tap looks like it targets the top-level search entry point.
     */
    private fun handlePossibleSearchTap(event: AccessibilityEvent) {
        val source = event.source ?: return
        val looksLikeSearchEntry = isSearchEntryPoint(source)
        source.recycle()
        if (!looksLikeSearchEntry) return

        handler.postDelayed({ checkForGlobalSearchNow() }, POST_CLICK_CHECK_DELAY_MS)
    }

    private fun isSearchEntryPoint(node: AccessibilityNodeInfo): Boolean {
        val resId = node.viewIdResourceName?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (SEARCH_ENTRY_ID_HINTS.any { resId.contains(it) }) return true
        if (desc == "search" || desc.contains("cari")) return true
        return false
    }

    /**
     * Walk the currently visible Telegram window and, if it looks like
     * Global Search results are showing, bounce back to the chat list.
     * Silent: no toast, no vibration, no dialog (per Silent Mode / FR-10).
     */
    private fun checkForGlobalSearchNow() {
        val root = rootInActiveWindow ?: return
        try {
            if (containsGlobalSearchSignature(root)) {
                bounceBackSilently()
            }
        } finally {
            root.recycle()
        }
    }

    private fun containsGlobalSearchSignature(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 40) return false // safety guard against pathological trees

        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()
        val resId = node.viewIdResourceName?.lowercase()

        if (text != null && GLOBAL_RESULT_SIGNATURES.any { text == it || text.startsWith(it) }) {
            return true
        }
        if (desc != null && GLOBAL_RESULT_SIGNATURES.any { desc.contains(it) }) {
            return true
        }
        if (resId != null && (resId.contains("global_search") || resId.contains("public_search"))) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = try {
                containsGlobalSearchSignature(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (found) return true
        }
        return false
    }

    private fun bounceBackSilently() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < BACK_DEBOUNCE_MS) return
        lastBackAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        // Second back shortly after in case Telegram needs two pops
        // (e.g. keyboard dismiss + fragment pop) to fully clear the
        // search fragment. Still silent — no UI shown to the user.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 120)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Safe Telegram guard interrupted")
    }
}
