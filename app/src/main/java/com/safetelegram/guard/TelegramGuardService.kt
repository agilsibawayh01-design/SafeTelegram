package com.safetelegram.guard.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.safetelegram.guard.domain.ArgoSearchDetector
import com.safetelegram.guard.domain.ContentDescriptionSearchEntryPointDetector
import com.safetelegram.guard.domain.GlobalSearchDetector
import com.safetelegram.guard.domain.RateLimiter
import com.safetelegram.guard.domain.SearchEntryPointDetector
import com.safetelegram.guard.domain.SignatureArgoSearchDetector
import com.safetelegram.guard.domain.TextSignatureGlobalSearchDetector
import com.safetelegram.guard.infra.AccessibilityNodeInfoAdapter

class TelegramGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val globalSearchDetector: GlobalSearchDetector = TextSignatureGlobalSearchDetector()
    private val entryPointDetector: SearchEntryPointDetector = ContentDescriptionSearchEntryPointDetector()

    private val treeWalkLimiter = RateLimiter(TREE_WALK_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }
    private val backActionLimiter = RateLimiter(BACK_ACTION_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }

    // Argo Search Auto-Exit — instance terpisah dari Global Search, sengaja
    // tidak berbagi state supaya tidak ada risiko saling memengaruhi.
    private val argoSearchDetector: ArgoSearchDetector = SignatureArgoSearchDetector()
    private val argoTreeWalkLimiter = RateLimiter(ARGO_TREE_WALK_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }
    private val argoBackActionLimiter = RateLimiter(ARGO_BACK_ACTION_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }

    companion object {
        private const val TAG = "SafeTelegramGuard"

        private const val TREE_WALK_MIN_INTERVAL_MS = 250L

        private const val BACK_ACTION_MIN_INTERVAL_MS = 400L

        private const val POST_CLICK_CHECK_DELAY_MS = 180L

        // Argo Search: cooldown lebih longgar dari Global Search karena
        // layar tujuan setelah back (daftar chat) bisa saja masih
        // menampilkan baris "Argo Search" sesaat.
        private const val ARGO_TREE_WALK_MIN_INTERVAL_MS = 800L
        private const val ARGO_BACK_ACTION_MIN_INTERVAL_MS = 800L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Safe Telegram guard connected (Telegram-scoped, no cross-app monitoring)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handlePossibleSearchTap(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkForGlobalSearchNow(force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (eventLooksRelevant(event)) checkForGlobalSearchNow(force = false)
            }
        }

        // --- TAMBAHAN: Argo Search Auto-Exit ---
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkForArgoSearchNow()
        }
    }

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

    private fun bounceBackSilently() {
        if (!backActionLimiter.tryAcquire()) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 120)
    }

    private fun checkForArgoSearchNow() {
        if (!argoTreeWalkLimiter.tryAcquire()) return

        val root = rootInActiveWindow ?: return
        val adapter = AccessibilityNodeInfoAdapter(root)
        try {
            if (argoSearchDetector.isArgoSearchVisible(adapter)) {
                bounceBackFromArgoSilently()
            }
        } finally {
            adapter.recycle()
        }
    }

    /** Satu kali GLOBAL_ACTION_BACK saja — tanpa double-back. */
    private fun bounceBackFromArgoSilently() {
        if (!argoBackActionLimiter.tryAcquire()) return
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Safe Telegram guard interrupted")
    }
}
