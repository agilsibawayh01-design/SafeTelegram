package com.safetelegram.guard.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.safetelegram.guard.domain.ContentDescriptionSearchEntryPointDetector
import com.safetelegram.guard.domain.GlobalSearchDetector
import com.safetelegram.guard.domain.RateLimiter
import com.safetelegram.guard.domain.SearchEntryPointDetector
import com.safetelegram.guard.domain.TextSignatureGlobalSearchDetector
import com.safetelegram.guard.infra.AccessibilityNodeInfoAdapter

/**
 * Safe Telegram — TelegramGuardService (v2, hardened)
 *
 * Scope guarantee (Requirement #1): this service NEVER receives events from
 * any package other than Telegram. That is not an app-side `if` check — it
 * is enforced by the OS at the binder level via `android:packageNames` in
 * accessibility_service_config.xml. The system simply never dispatches
 * events for other apps to this service, which means we structurally cannot
 * read another app's UI (including banking apps), and there is zero CPU/RAM
 * cost while any non-Telegram app is in the foreground. This is the
 * strongest form of scoping available on Android — stronger than any
 * runtime foreground-app check we could add ourselves.
 *
 * Everything else in this class exists to make the *few* events we do
 * receive as cheap as possible (Requirement #2/#5): cheap event-level
 * pre-filtering before ever touching the node tree, and rate limiting so a
 * burst of TYPE_WINDOW_CONTENT_CHANGED events (e.g. new messages arriving,
 * the user typing) triggers at most one tree walk per [TREE_WALK_MIN_INTERVAL_MS].
 */
class TelegramGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val globalSearchDetector: GlobalSearchDetector = TextSignatureGlobalSearchDetector()
    private val entryPointDetector: SearchEntryPointDetector = ContentDescriptionSearchEntryPointDetector()

    private val treeWalkLimiter = RateLimiter(TREE_WALK_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }
    private val backActionLimiter = RateLimiter(BACK_ACTION_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }

    companion object {
        private const val TAG = "SafeTelegramGuard"

        // How often we're willing to do a full node-tree walk in response to
        // TYPE_WINDOW_CONTENT_CHANGED spam. This is the single biggest lever
        // for CPU usage: without it, every keystroke/message re-triggers a
        // full recursive scan.
        private const val TREE_WALK_MIN_INTERVAL_MS = 250L

        // Debounce for GLOBAL_ACTION_BACK so one detection can't fire it
        // repeatedly while navigation is still settling.
        private const val BACK_ACTION_MIN_INTERVAL_MS = 400L

        private const val POST_CLICK_CHECK_DELAY_MS = 180L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Safe Telegram guard connected (Telegram-scoped, no cross-app monitoring)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Cheap pre-filter using the event object itself — no node tree
        // traversal here. event.text / event.contentDescription is populated
        // by the framework from the node that changed, at effectively no
        // extra cost, so we can skip a full tree walk for the large fraction
        // of content-change events that obviously aren't search-related
        // (message bubbles rendering, timestamps ticking, etc).
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handlePossibleSearchTap(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkForGlobalSearchNow(force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (eventLooksRelevant(event)) checkForGlobalSearchNow(force = false)
            }
        }
    }

    /**
     * Fast reject using only the AccessibilityEvent's own text/description
     * (already delivered to us — no extra API call), before we decide
     * whether a full tree walk is warranted.
     */
    private fun eventLooksRelevant(event: AccessibilityEvent): Boolean {
        val combined = buildString {
            event.text?.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it) }
        }.lowercase()
        return combined.contains("search") || combined.contains("cari") || combined.contains("global")
    }

    private fun handlePossibleSearchTap(event: AccessibilityEvent) {
        val source = event.source ?: return
        val adapter = AccessibilityNodeInfoAdapter(source)
        val looksLikeSearchEntry = entryPointDetector.isSearchEntryPoint(adapter)
        adapter.recycle()
        if (!looksLikeSearchEntry) return

        handler.postDelayed({ checkForGlobalSearchNow(force = true) }, POST_CLICK_CHECK_DELAY_MS)
    }

    private fun checkForGlobalSearchNow(force: Boolean) {
        if (!force && !treeWalkLimiter.tryAcquire()) return

        val root = rootInActiveWindow ?: return
        val adapter = AccessibilityNodeInfoAdapter(root)
        try {
            if (globalSearchDetector.isGlobalSearchVisible(adapter)) {
                bounceBackSilently()
            }
        } finally {
            adapter.recycle()
        }
    }

    /** Silent Mode: no dialog, no toast, no sound — just navigate back. */
    private fun bounceBackSilently() {
        if (!backActionLimiter.tryAcquire()) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 120)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Safe Telegram guard interrupted")
    }
}
