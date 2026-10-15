package com.webapp.app

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity(), InAppBrowser.Host {

    private lateinit var rootLayout: View
    private lateinit var contentFrame: View
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var themeButton: View
    private lateinit var inAppBrowser: InAppBrowser
    private lateinit var themeSheet: ThemeSheet

    private val brandColor by lazy { ContextCompat.getColor(this, R.color.brand) }
    private val isBrandLight by lazy { ColorUtils.calculateLuminance(brandColor) > 0.5 }
    private val accentColor by lazy { ThemeColors.accent(this, brandColor) }

    /** Base domain of the website: everything that belongs to it stays inside the app. */
    private val baseDomain by lazy {
        Uri.parse(BuildConfig.APP_URL).host.orEmpty().removePrefix("www.")
    }

    private var isFirstLoadComplete = false
    private var lastFailedUrl: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        setupSystemBars()
        setContentView(R.layout.activity_main)

        // After a recreation (e.g. a theme change) the page is already there: no splash wait
        isFirstLoadComplete = savedInstanceState != null
        splashScreen.setKeepOnScreenCondition { !isFirstLoadComplete }
        window.decorView.postDelayed({ isFirstLoadComplete = true }, SPLASH_TIMEOUT_MS)

        bindViews()
        applyAccentColor()
        applyWindowInsets()
        setupWebView()
        setupSwipeRefresh()
        setupInAppBrowser()
        setupThemeControls()
        setupBackNavigation()

        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) webView.loadUrl(BuildConfig.APP_URL)
        inAppBrowser.restoreState(savedInstanceState)
    }

    // ───────────────────────────── UI setup ─────────────────────────────

    private fun setupSystemBars() {
        val style = if (isBrandLight) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** Status bar icons must contrast with the brand color, or with the in-app browser surface. */
    private fun updateSystemBarIcons(browserVisible: Boolean) {
        val isBackgroundLight = if (browserVisible) !ThemeColors.isNightMode(this) else isBrandLight
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isBackgroundLight
            isAppearanceLightNavigationBars = isBackgroundLight
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.root)
        contentFrame = findViewById(R.id.contentFrame)
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        themeButton = findViewById(R.id.themeButton)

        findViewById<Button>(R.id.retryButton).setOnClickListener { retry() }
    }

    private fun applyAccentColor() {
        val accentTint = ColorStateList.valueOf(accentColor)
        progressBar.progressTintList = accentTint
        findViewById<ImageView>(R.id.errorIcon).imageTintList = accentTint
        findViewById<Button>(R.id.retryButton).apply {
            backgroundTintList = accentTint
            setTextColor(ThemeColors.contentColorOn(accentColor))
        }
    }

    /** Keeps every layer clear of the status bar, navigation bar, and keyboard. */
    private fun applyWindowInsets() {
        val browserOverlay = findViewById<View>(R.id.browserOverlay)
        val browserBar = findViewById<View>(R.id.browserBar)
        val themeCard = findViewById<View>(R.id.themeCard)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, keyboard.bottom)

            contentFrame.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bottom)
            // The browser bar extends under the navigation bar, like Firefox
            browserOverlay.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            browserBar.updatePadding(bottom = bottom)
            themeCard.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupWebView() {
        WebViewDefaults.apply(webView, isTrustedSite = true)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.isEnabled = BuildConfig.ENABLE_PULL_TO_REFRESH
        swipeRefresh.setColorSchemeColors(accentColor)
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_elevated)
        )
        swipeRefresh.setOnRefreshListener { webView.reload() }
        // Only allow pull-to-refresh when the page is scrolled to the top
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
    }

    private fun setupInAppBrowser() {
        inAppBrowser = InAppBrowser(this, findViewById(R.id.browserOverlay), accentColor, this)
    }

    private fun setupThemeControls() {
        themeSheet = ThemeSheet(findViewById(R.id.themeSheet), accentColor, ::applyThemeMode)

        themeButton.isVisible = BuildConfig.SHOW_THEME_BUTTON
        themeButton.enableDragToReposition()
        themeButton.setOnClickListener { showThemeSheet() }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    themeSheet.isOpen -> themeSheet.hide()
                    inAppBrowser.isOpen -> inAppBrowser.goBackOrClose()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ───────────────────────────── Theme ─────────────────────────────

    private fun showThemeSheet() = themeSheet.show(ThemeManager.getMode(this))

    private fun applyThemeMode(mode: ThemeMode) {
        // Short delay so the user sees the check mark before the sheet closes
        themeSheet.hide(startDelay = THEME_APPLY_DELAY_MS) {
            if (mode == ThemeManager.getMode(this)) return@hide
            // Cross-fades the activity while it is recreated with the new colors
            window.setWindowAnimations(R.style.ThemeChangeAnimation)
            ThemeManager.setMode(this, mode)
        }
    }

    private fun setThemeButtonVisible(visible: Boolean) {
        if (!BuildConfig.SHOW_THEME_BUTTON) return
        themeButton.animate().cancel()
        if (visible) {
            themeButton.isVisible = true
            themeButton.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
        } else {
            themeButton.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(160)
                .withEndAction { themeButton.isVisible = false }
                .start()
        }
    }

    // ───────────────────────────── Navigation ─────────────────────────────

    override fun isInternalUrl(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host ?: return false
        return host == baseDomain || host.endsWith(".$baseDomain")
    }

    /** HTTPS links go to the in-app browser; everything else is handed off to system apps. */
    private fun handleExternalLink(uri: Uri) {
        if (BuildConfig.OPEN_LINKS_IN_APP && uri.scheme == "https") {
            inAppBrowser.open(uri.toString())
        } else {
            openExternally(uri)
        }
    }

    /** External links, tel:, mailto:, WhatsApp, Maps, etc. are handed off to system apps. */
    override fun openExternally(uri: Uri) {
        val intent = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }
                .getOrNull()
                ?.apply {
                    // Security: prevents the website from launching other apps' internal components
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
                ?: return
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
            when {
                fallbackUrl == null ->
                    Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show()
                isInternalUrl(Uri.parse(fallbackUrl)) -> webView.loadUrl(fallbackUrl)
                else -> handleExternalLink(Uri.parse(fallbackUrl))
            }
        }
    }

    override fun openInMainWebView(url: String) {
        showError(false)
        webView.loadUrl(url)
    }

    override fun onBrowserVisibilityChanged(visible: Boolean) {
        updateSystemBarIcons(browserVisible = visible)
        setThemeButtonVisible(!visible)
    }

    private fun showError(visible: Boolean) {
        errorView.isVisible = visible
        swipeRefresh.isRefreshing = false
        progressBar.isVisible = false
    }

    private fun retry() {
        showError(false)
        webView.loadUrl(lastFailedUrl ?: BuildConfig.APP_URL)
    }

    // ───────────────────────────── Download & files ─────────────────────────────

    override fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        // blob:/data: URLs and Android < 10 are handed off to the system browser
        if (!URLUtil.isNetworkUrl(url) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            openExternally(Uri.parse(url))
            return
        }

        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(this, getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
    }

    override fun showFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }

    // ───────────────────────────── Fullscreen video ─────────────────────────────

    override fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fullscreenContainer.isVisible = true
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun exitFullscreen() {
        if (customView == null) return
        fullscreenContainer.removeAllViews()
        fullscreenContainer.isVisible = false
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    // ───────────────────────────── WebView clients ─────────────────────────────

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when {
                isInternalUrl(uri) -> false
                // A link to "webapp://theme" on your site opens the theme picker
                uri.scheme == APP_SCHEME && uri.host == THEME_SETTINGS_HOST -> {
                    showThemeSheet()
                    true
                }
                else -> {
                    handleExternalLink(uri)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progressBar.isVisible = true
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.isVisible = false
            swipeRefresh.isRefreshing = false
            isFirstLoadComplete = true
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                lastFailedUrl = request.url.toString()
                isFirstLoadComplete = true
                showError(true)
            }
        }
    }

    private inner class AppChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            progressBar.isVisible = newProgress < 100
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean = showFileChooser(filePathCallback, fileChooserParams)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) =
            enterFullscreen(view, callback)

        override fun onHideCustomView() = exitFullscreen()
    }

    // ───────────────────────────── Lifecycle ─────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        inAppBrowser.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        inAppBrowser.onResume()
    }

    override fun onPause() {
        inAppBrowser.onPause()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        inAppBrowser.destroy()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_TIMEOUT_MS = 4000L
        const val THEME_APPLY_DELAY_MS = 160L
        const val APP_SCHEME = "webapp"
        const val THEME_SETTINGS_HOST = "theme"
    }
}
