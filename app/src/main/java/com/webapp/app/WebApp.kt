package com.webapp.app

import android.app.Application

class WebApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Applied before any activity is created, so the first frame already uses the right theme
        ThemeManager.applySaved(this)
    }
}
