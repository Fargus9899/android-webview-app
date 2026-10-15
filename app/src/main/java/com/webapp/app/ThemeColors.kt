package com.webapp.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

object ThemeColors {

    fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Brand color adjusted to stay visible on the current background:
     * a very dark brand is lightened in dark mode, a very light one is darkened in light mode.
     */
    @ColorInt
    fun accent(context: Context, @ColorInt brand: Int): Int {
        val luminance = ColorUtils.calculateLuminance(brand)
        val isNight = isNightMode(context)
        return when {
            isNight && luminance < 0.2 -> ColorUtils.blendARGB(brand, Color.WHITE, 0.62f)
            !isNight && luminance > 0.8 -> ColorUtils.blendARGB(brand, Color.BLACK, 0.62f)
            else -> brand
        }
    }

    @ColorInt
    fun contentColorOn(@ColorInt background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > 0.5) Color.BLACK else Color.WHITE
}
