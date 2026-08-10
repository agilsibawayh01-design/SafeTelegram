package com.safetelegram.guard.infra

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.safetelegram.guard.domain.ScreenNode

/**
 * The single adapter point between the Android accessibility framework and
 * our domain layer. Nothing outside this file (and the service that owns
 * the root node's lifecycle) should import android.view.accessibility.*.
 *
 * Node recycling: [AccessibilityNodeInfo.recycle] is required on API < 33 to
 * avoid exhausting the node pool, and is a documented no-op from API 33
 * onward (recycling is automatic). We keep calling it for correctness on
 * API 29–32, which this app still supports (minSdk 29).
 */
class AccessibilityNodeInfoAdapter(
    private val node: AccessibilityNodeInfo
) : ScreenNode {

    override val text: String?
        get() = node.text?.toString()

    override val contentDescription: String?
        get() = node.contentDescription?.toString()

    override val childCount: Int
        get() = node.childCount

    override fun childAt(index: Int): ScreenNode? {
        val child = node.getChild(index) ?: return null
        return AccessibilityNodeInfoAdapter(child)
    }

    /** Recycles this node's underlying framework object (see class doc). */
    fun recycle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
