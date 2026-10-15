package com.webapp.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible

/** Bottom sheet to choose between Automatic, Light and Dark theme. */
class ThemeSheet(
    private val sheet: View,
    @ColorInt private val accentColor: Int,
    private val onModeSelected: (ThemeMode) -> Unit
) {

    private class OptionViews(val container: View, val check: View)

    private val scrim: View = sheet.findViewById(R.id.themeScrim)
    private val card: View = sheet.findViewById(R.id.themeCard)

    private val options: Map<ThemeMode, OptionViews> = mapOf(
        ThemeMode.SYSTEM to OptionViews(sheet.findViewById(R.id.optionSystem), sheet.findViewById(R.id.checkSystem)),
        ThemeMode.LIGHT to OptionViews(sheet.findViewById(R.id.optionLight), sheet.findViewById(R.id.checkLight)),
        ThemeMode.DARK to OptionViews(sheet.findViewById(R.id.optionDark), sheet.findViewById(R.id.checkDark))
    )

    var isOpen = false
        private set

    init {
        scrim.setOnClickListener { hide() }
        options.forEach { (mode, views) ->
            views.container.background = createOptionBackground()
            views.check.backgroundTintList = ColorStateList.valueOf(accentColor)
            views.container.setOnClickListener {
                if (!isOpen) return@setOnClickListener
                select(mode, animate = true)
                onModeSelected(mode)
            }
        }
    }

    fun show(current: ThemeMode) {
        if (isOpen) return
        isOpen = true
        select(current, animate = false)

        scrim.animate().cancel()
        card.animate().cancel()
        sheet.isVisible = true

        scrim.alpha = 0f
        scrim.animate().alpha(1f).setStartDelay(0).setDuration(250).start()

        card.doOnLayout {
            it.translationY = it.height.toFloat()
            it.animate()
                .translationY(0f)
                .setStartDelay(0)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .setDuration(400)
                .start()
        }
    }

    fun hide(startDelay: Long = 0L, onHidden: (() -> Unit)? = null) {
        if (!isOpen) return
        isOpen = false

        scrim.animate().alpha(0f).setStartDelay(startDelay).setDuration(220).start()
        card.animate()
            .translationY(card.height.toFloat())
            .setStartDelay(startDelay)
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .setDuration(220)
            .withEndAction {
                sheet.isVisible = false
                onHidden?.invoke()
            }
            .start()
    }

    private fun select(mode: ThemeMode, animate: Boolean) {
        options.forEach { (optionMode, views) ->
            val isSelected = optionMode == mode
            val target = if (isSelected) 1f else 0f
            views.container.isSelected = isSelected
            views.check.animate().cancel()

            if (animate) {
                views.check.animate()
                    .scaleX(target).scaleY(target).alpha(target)
                    .setInterpolator(OvershootInterpolator(2f))
                    .setDuration(260)
                    .start()
            } else {
                views.check.scaleX = target
                views.check.scaleY = target
                views.check.alpha = target
            }
        }
    }

    /** Rounded card with an accent border when selected, plus a ripple on touch. */
    private fun createOptionBackground(): Drawable {
        val context = sheet.context
        val radius = context.resources.getDimension(R.dimen.theme_option_radius)
        val strokeWidth = context.resources.getDimensionPixelSize(R.dimen.theme_option_stroke)
        val containerColor = ContextCompat.getColor(context, R.color.surface_container)
        val outlineColor = ContextCompat.getColor(context, R.color.outline)
        val selectedFill = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(accentColor, 0x24),
            containerColor
        )

        fun shape(@ColorInt fill: Int, @ColorInt stroke: Int) = GradientDrawable().apply {
            cornerRadius = radius
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }

        val states = StateListDrawable().apply {
            setEnterFadeDuration(180)
            setExitFadeDuration(180)
            addState(intArrayOf(android.R.attr.state_selected), shape(selectedFill, accentColor))
            addState(intArrayOf(), shape(containerColor, outlineColor))
        }
        val mask = shape(Color.WHITE, Color.WHITE)
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 0x33))
        return RippleDrawable(rippleColor, states, mask)
    }

    private companion object {
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}
