package com.safetelegram.guard.domain

/**
 * Simple "not more than once per [minIntervalMs]" gate. Injected time source
 * keeps this unit-testable (no Robolectric / SystemClock needed).
 *
 * Used for two separate purposes in the service:
 *  1. Throttling how often we do a full tree walk in response to noisy
 *     TYPE_WINDOW_CONTENT_CHANGED events (this is the main CPU cost).
 *  2. Debouncing GLOBAL_ACTION_BACK so a single detection doesn't fire it
 *     repeatedly while the back-navigation is still settling.
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val nowMs: () -> Long
) {
    private var lastFiredAt = 0L

    fun tryAcquire(): Boolean {
        val now = nowMs()
        if (now - lastFiredAt < minIntervalMs) return false
        lastFiredAt = now
        return true
    }
}
