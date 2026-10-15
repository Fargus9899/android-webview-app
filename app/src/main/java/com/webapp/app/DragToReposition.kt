package com.webapp.app

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import kotlin.math.hypot

private const val PRESSED_SCALE = 0.9f

private data class DragBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

/**
 * Lets the user drag a floating button anywhere inside its parent; on release it snaps
 * to the nearest side. A simple tap still triggers the normal click listener.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.enableDragToReposition() {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downRawX = 0f
    var downRawY = 0f
    var startX = 0f
    var startY = 0f
    var isDragging = false

    setOnTouchListener { view, event ->
        val parent = view.parent as? View ?: return@setOnTouchListener false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = view.translationX
                startY = view.translationY
                isDragging = false
                view.isPressed = true
                view.animate().cancel()
                view.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE).setDuration(100).start()
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!isDragging && hypot(dx, dy) > touchSlop) {
                    isDragging = true
                    view.isPressed = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging) {
                    val bounds = view.dragBounds(parent)
                    view.translationX = (startX + dx).coerceIn(bounds.minX, bounds.maxX)
                    view.translationY = (startY + dy).coerceIn(bounds.minY, bounds.maxY)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                if (isDragging) {
                    view.snapToNearestSide(parent)
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
        }
        true
    }
}

private fun View.snapToNearestSide(parent: View) {
    val bounds = dragBounds(parent)
    val centerX = x + width / 2f
    val targetX = if (centerX < parent.width / 2f) bounds.minX else bounds.maxX
    animate()
        .translationX(targetX)
        .scaleX(1f)
        .scaleY(1f)
        .setInterpolator(OvershootInterpolator(1.1f))
        .setDuration(320)
        .start()
}

/** Translation limits that keep the view inside the parent, margins included. */
private fun View.dragBounds(parent: View): DragBounds {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams
    return DragBounds(
        minX = (parent.paddingLeft + (lp?.leftMargin ?: 0) - left).toFloat(),
        maxX = (parent.width - parent.paddingRight - (lp?.rightMargin ?: 0) - right).toFloat(),
        minY = (parent.paddingTop + (lp?.topMargin ?: 0) - top).toFloat(),
        maxY = (parent.height - parent.paddingBottom - (lp?.bottomMargin ?: 0) - bottom).toFloat()
    )
}
