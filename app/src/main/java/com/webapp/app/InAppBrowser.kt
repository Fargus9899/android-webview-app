package com.webapp.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible

/**
 * Lightweight browser tab shown on top of the app (Firefox web-app style):
 * it slides up from the bottom and has a bottom bar with the close button on the left.
 */
class InAppBrowser(
    private val activity: AppCompatActivity,
    private val overlay: View,
    @ColorInt accentColor: Int,
    private val host: Host
) {

    /** What the browser needs from the activity that hosts it. */
    interface Host {
        fun isInternalUrl(uri: Uri): Boolean
        fun openInMainWebView(url: String)
        fun openExternally(uri: Uri)
        fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String)
        fun showFileChooser(
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams
        ): Boolean
        fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
        fun exitFullscreen()
        fun onBrowserVisibilityChanged(visible: Boolean)
    }

    private val contentContainer: FrameLayout = overlay.findViewById(R.id.browserContent)
    private val progressBar: ProgressBar = overlay.findViewById(R.id.browserProgress)
    private val titleView: TextView = overlay.findViewById(R.id.browserTitle)
    private val domainView: TextView = overlay.findViewById(R.id.browserDomain)
    private val lockIcon: ImageView = overlay.findViewById(R.id.browserLock)
    private val menuButton: View = overlay.findViewById(R.id.browserMenu)

    private var webView: WebView? = null
    private var closingWebView: WebView? = null
    private var isShown = false

    val isOpen: Boolean get() = webView != null
    private val currentUrl: String? get() = webView?.url

    init {
        progressBar.progressTintList = ColorStateList.valueOf(accentColor)
        overlay.findViewById<View>(R.id.browserClose).setOnClickListener { close() }
        overlay.findViewById<View>(R.id.browserShare).setOnClickListener { shareCurrentPage() }
        overlay.findViewById<View>(R.id.browserTitleBlock).setOnClickListener { showMenu() }
        menuButton.setOnClickListener { showMenu() }
    }

    // ───────────────────────────── Open / close ─────────────────────────────

    fun open(url: String, animate: Boolean = true) {
        destroyClosingWebView()
        val browser = webView ?: createWebView().also {
            webView = it
            contentContainer.addView(
                it,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        updateHeader(url, title = null)
        browser.loadUrl(url)
        showOverlay(animate)
    }

    fun close() {
        val browser = webView ?: return
        webView = null
        closingWebView = browser
        browser.stopLoading()
        browser.onPause() // Stops audio and video immediately

        isShown = false
        host.onBrowserVisibilityChanged(false)

        overlay.animate().cancel()
        overlay.animate()
            .translationY(slideDistance())
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .setDuration(CLOSE_DURATION_MS)
            .withEndAction {
                overlay.isVisible = false
                destroyClosingWebView()
                resetHeader()
            }
            .start()
    }

    /** Back button: goes back in the tab history, or closes the tab. */
    fun goBackOrClose() {
        val browser = webView ?: return
        if (browser.canGoBack()) browser.goBack() else close()
    }

    private fun showOverlay(animate: Boolean) {
        overlay.animate().cancel()
        if (!overlay.isVisible) overlay.translationY = slideDistance()
        overlay.isVisible = true

        if (!isShown) {
            isShown = true
            host.onBrowserVisibilityChanged(true)
        }

        if (!animate) {
            overlay.translationY = 0f
            return
        }
        overlay.animate()
            .translationY(0f)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .setDuration(OPEN_DURATION_MS)
            .start()
    }

    private fun slideDistance(): Float {
        val parentHeight = (overlay.parent as? View)?.height ?: 0
        return (if (parentHeight > 0) parentHeight else overlay.resources.displayMetrics.heightPixels).toFloat()
    }

    private fun destroyClosingWebView() {
        closingWebView?.let {
            contentContainer.removeView(it)
            it.destroy()
        }
        closingWebView = null
    }

    // ───────────────────────────── WebView ─────────────────────────────

    private fun createWebView(): WebView = WebView(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.surface))
        WebViewDefaults.apply(this, isTrustedSite = false)
        webViewClient = BrowserClient()
        webChromeClient = BrowserChromeClient()
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private inner class BrowserClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when {
                // tel:, mailto:, intent:, http:// (blocked in-app) → system apps
                uri.scheme != "https" -> {
                    host.openExternally(uri)
                    true
                }
                // Back on your website (e.g. after a login): continue in the main app
                request.isForMainFrame && host.isInternalUrl(uri) -> {
                    host.openInMainWebView(uri.toString())
                    close()
                    true
                }
                else -> false
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view != webView) return
            progressBar.isInvisible = false
            updateHeader(url, view.title)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view != webView) return
            progressBar.isInvisible = true
            updateHeader(url, view.title)
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view != webView || !request.isForMainFrame) return
            progressBar.isInvisible = true
            titleView.setText(R.string.browser_error)
        }
    }

    private inner class BrowserChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view != webView) return
            progressBar.setProgress(newProgress, true)
            progressBar.isInvisible = newProgress >= 100
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view == webView) updateHeader(view.url, title)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean = host.showFileChooser(filePathCallback, fileChooserParams)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) =
            host.enterFullscreen(view, callback)

        override fun onHideCustomView() = host.exitFullscreen()
    }

    // ───────────────────────────── Bottom bar ─────────────────────────────

    private fun updateHeader(url: String?, title: String?) {
        val uri = url?.let(Uri::parse)
        val domain = uri?.host.orEmpty().removePrefix("www.")

        domainView.text = domain
        lockIcon.isVisible = uri?.scheme == "https"

        // Before loading, WebView reports the URL as title: show the domain instead
        val isRealTitle = !title.isNullOrBlank() && url?.contains(title) != true
        titleView.text = when {
            isRealTitle -> title
            domain.isNotEmpty() -> domain
            else -> activity.getString(R.string.browser_loading)
        }
    }

    private fun resetHeader() {
        titleView.text = null
        domainView.text = null
        progressBar.progress = 0
    }

    private fun showMenu() {
        PopupMenu(activity, menuButton, Gravity.END).apply {
            inflate(R.menu.menu_in_app_browser)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_reload -> webView?.reload()
                    R.id.action_copy_link -> copyLink()
                    R.id.action_open_external -> openInSystemBrowser()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }.show()
    }

    private fun shareCurrentPage() {
        val url = currentUrl ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            webView?.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        activity.startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun copyLink() {
        val url = currentUrl ?: return
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
        // Android 13+ already shows its own confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, R.string.browser_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInSystemBrowser() {
        val url = currentUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            activity.startActivity(intent)
            close()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    // ───────────────────────────── Lifecycle ─────────────────────────────

    fun saveState(outState: Bundle) {
        currentUrl?.let { outState.putString(KEY_URL, it) }
    }

    fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.getString(KEY_URL)?.let { open(it, animate = false) }
    }

    fun onResume() = webView?.onResume()

    fun onPause() = webView?.onPause()

    fun destroy() {
        destroyClosingWebView()
        webView?.let {
            contentContainer.removeView(it)
            it.destroy()
        }
        webView = null
    }

    private companion object {
        const val KEY_URL = "in_app_browser_url"
        const val OPEN_DURATION_MS = 420L
        const val CLOSE_DURATION_MS = 240L

        // Material 3 "emphasized" curves: fast start and soft landing (and vice versa)
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}
