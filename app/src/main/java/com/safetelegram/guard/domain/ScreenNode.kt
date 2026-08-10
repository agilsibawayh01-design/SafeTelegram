package com.safetelegram.guard.domain

/**
 * A framework-agnostic view of one node in a screen's UI tree.
 *
 * Why this exists: [android.view.accessibility.AccessibilityNodeInfo] is an
 * Android framework type that is expensive to construct in tests (requires
 * Robolectric/instrumentation) and, more importantly, ties our detection
 * *policy* to a concrete framework *mechanism*. By depending on this
 * interface instead, the domain layer (what counts as "Global Search is
 * visible") has zero Android dependencies and is trivially unit-testable
 * with plain fakes.
 *
 * The infra layer (see [com.safetelegram.guard.infra.AccessibilityNodeInfoAdapter])
 * is the only place that touches the real framework type.
 */
interface ScreenNode {
    val text: String?
    val contentDescription: String?
    val childCount: Int
    fun childAt(index: Int): ScreenNode?
}
