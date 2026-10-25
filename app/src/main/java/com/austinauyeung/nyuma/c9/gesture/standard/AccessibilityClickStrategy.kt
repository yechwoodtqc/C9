package com.austinauyeung.nyuma.c9.gesture.standard

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.austinauyeung.nyuma.c9.core.domain.ScrollDirection
import com.austinauyeung.nyuma.c9.core.logs.Logger
import com.austinauyeung.nyuma.c9.gesture.api.GestureCompletionListener
import com.austinauyeung.nyuma.c9.gesture.api.GestureStrategy

/**
 * Android 6 fallback using only public AccessibilityNodeInfo actions.
 *
 * This can activate accessible controls and scrollable containers. It cannot
 * synthesize arbitrary touchscreen gestures or guarantee clicks on custom-drawn
 * surfaces that expose no accessibility nodes.
 */
class AccessibilityClickStrategy(
    private val service: AccessibilityService
) : GestureStrategy {
    private var pendingTapX = 0f
    private var pendingTapY = 0f

    private fun contains(node: AccessibilityNodeInfo, x: Float, y: Float): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.contains(x.toInt(), y.toInt())
    }

    private fun clickNodeAt(node: AccessibilityNodeInfo?, x: Float, y: Float): Boolean {
        if (node == null) return false
        try {
            if (!contains(node, x, y)) return false

            // Prefer the most specific child under the requested point.
            for (index in node.childCount - 1 downTo 0) {
                val child = node.getChild(index) ?: continue
                val clicked = try {
                    clickNodeAt(child, x, y)
                } finally {
                    child.recycle()
                }
                if (clicked) return true
            }

            val supportsClick = node.isClickable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            return supportsClick && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Logger.e("Accessibility click failed at ($x, $y)", e)
            return false
        }
    }

    private fun clickAt(x: Float, y: Float): Boolean {
        val root = service.rootInActiveWindow
        if (root == null) {
            Logger.w("Accessibility click unavailable: no active window tree")
            return false
        }
        return try {
            clickNodeAt(root, x, y)
        } finally {
            root.recycle()
        }
    }

    private fun scrollAt(x: Float, y: Float, direction: ScrollDirection): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val action = when (direction) {
            ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        fun findScrollable(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            try {
                if (contains(node, x, y) && node.isScrollable &&
                    node.performAction(action)
                ) return true
                for (index in node.childCount - 1 downTo 0) {
                    val child = node.getChild(index) ?: continue
                    val result = try { findScrollable(child) } finally { child.recycle() }
                    if (result) return true
                }
            } catch (e: Exception) {
                Logger.e("Accessibility scroll action failed", e)
            }
            return false
        }
        return try { findScrollable(root) } finally { root.recycle() }
    }

    override suspend fun immediateTap(x: Float, y: Float): Boolean = clickAt(x, y)

    override suspend fun startTap(
        x: Float,
        y: Float,
        completionListener: GestureCompletionListener?
    ): Boolean {
        pendingTapX = x
        pendingTapY = y
        completionListener?.onGestureCompleted(true)
        return true
    }

    override suspend fun endTap(
        finalX: Float,
        finalY: Float,
        completionListener: GestureCompletionListener?
    ): Boolean {
        val success = clickAt(finalX, finalY)
        completionListener?.onGestureCompleted(success)
        return success
    }

    override suspend fun dragTap(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        completionListener: GestureCompletionListener?
    ): Boolean {
        Logger.w("Drag is not supported by the Android 6 accessibility-only strategy")
        completionListener?.onGestureCompleted(false)
        return false
    }

    override fun cancelTap(completionListener: GestureCompletionListener?): Boolean {
        completionListener?.onGestureCompleted(true)
        return true
    }

    override suspend fun performScroll(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        forceFixedGesture: Boolean,
        duration: Long,
        completionListener: GestureCompletionListener?
    ): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val direction = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx < 0) ScrollDirection.LEFT else ScrollDirection.RIGHT
        } else {
            if (dy < 0) ScrollDirection.UP else ScrollDirection.DOWN
        }
        val success = scrollAt(startX, startY, direction)
        completionListener?.onGestureCompleted(success)
        return success
    }

    override suspend fun performZoom(
        isZoomIn: Boolean,
        startX1: Float, startY1: Float,
        startX2: Float, startY2: Float,
        endX1: Float, endY1: Float,
        endX2: Float, endY2: Float,
        forceFixedGesture: Boolean,
        completionListener: GestureCompletionListener?
    ): Boolean {
        Logger.w("Zoom is not supported by the Android 6 accessibility-only strategy")
        completionListener?.onGestureCompleted(false)
        return false
    }
}
