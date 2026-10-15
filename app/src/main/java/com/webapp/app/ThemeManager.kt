package com.webapp.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromConfig(value: String): ThemeMode = when (value.trim().lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}

/** Stores the theme chosen by the user and applies it to the whole app. */
object ThemeManager {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getMode(context: Context): ThemeMode {
        val saved = prefs(context).getString(KEY_THEME_MODE, null)
        return saved?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.fromConfig(BuildConfig.DEFAULT_THEME)
    }

    /** Saves the mode and applies it: AppCompat recreates the activity if the colors change. */
    fun setMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context).nightMode)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
