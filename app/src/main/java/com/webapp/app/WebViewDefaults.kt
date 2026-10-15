package com.webapp.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Settings shared by the main WebView and the in-app browser. */
object WebViewDefaults {

    /**
     * @param isTrustedSite true for your own website, false for third-party pages
     *                      opened in the in-app browser (no autoplay).
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, isTrustedSite: Boolean) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = !isTrustedSite
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Lets the website detect that it's running inside the app
            userAgentString = "$userAgentString WebAppAndroid/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Sites with "prefers-color-scheme" follow the app theme automatically;
        // this also darkens sites that have no dark mode of their own.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                webView.settings,
                BuildConfig.AUTO_DARKEN_WEBSITES
            )
        }
    }
}
