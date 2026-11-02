package com.myjoint.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import java.net.URLEncoder

class MainActivity : Activity() {

    private data class BrowserTab(
        val web: WebView,
        val privateMode: Boolean,
        var showingHome: Boolean = true
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var active = 0

    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var pageHost: FrameLayout
    private lateinit var tabCounter: TextView

    private val purple = Color.rgb(104, 55, 205)
    private val dark = Color.rgb(19, 20, 28)
    private val white = Color.WHITE

    private val jointUrl = "https://myjointapp.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = dark
        window.navigationBarColor = dark

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        buildInterface()

        val shortcutUrl = intent.getStringExtra("shortcut_url")

        if (!shortcutUrl.isNullOrBlank()) {
            addTab(shortcutUrl, false)
        } else {
            addTab("", false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val shortcutUrl = intent.getStringExtra("shortcut_url")

        if (!shortcutUrl.isNullOrBlank()) {
            addTab(shortcutUrl, false)
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    // ==========================================
    // BROWSER INTERFACE
    // ==========================================

    private fun buildInterface() {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
        }

        // TOP HEADER

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(5), dp(5))
            setBackgroundColor(dark)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.joint_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "JOINT Logo"
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        val brand = TextView(this).apply {
            text = "JOINT"
            textSize = 18f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), 0, 0, 0)
        }

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        // HOME BUTTON

        button("⌂", header) {
            showHome()
        }.contentDescription = "Home"

        // REFRESH BUTTON

        button("↻", header) {
            refreshPage()
        }.contentDescription = "Refresh"

        // NEW TAB

        button("+", header) {
            addTab("", false)
        }.contentDescription = "New tab"

        // TAB COUNTER

        tabCounter = TextView(this).apply {
            text = "1"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            background = rounded(
                Color.rgb(43, 43, 55),
                9
            )
            minWidth = dp(34)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            contentDescription = "Open tabs"
            setOnClickListener {
                showTabs()
            }
        }

        header.addView(
            tabCounter,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
        )

        // MENU

        button("⋮", header) {
            showMenu()
        }.contentDescription = "Browser menu"

        root.addView(header)

        // ADDRESS AND GOOGLE SEARCH BAR

        val searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(
                Color.rgb(43, 43, 55),
                30
            )
        }

        val searchIcon = TextView(this).apply {
            text = "⌕"
            textSize = 29f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        searchContainer.addView(
            searchIcon,
            LinearLayout.LayoutParams(dp(35), dp(48))
        )

        address = EditText(this).apply {

            hint = "Search Google or enter website"

            setSingleLine(true)

            textSize = 14f

            setTextColor(white)

            setHintTextColor(Color.LTGRAY)

            setPadding(dp(5), 0, dp(5), 0)

            setBackgroundColor(Color.TRANSPARENT)

            imeOptions = EditorInfo.IME_ACTION_GO

            setOnEditorActionListener { _, action, _ ->

                if (
                    action == EditorInfo.IME_ACTION_GO ||
                    action == EditorInfo.IME_ACTION_DONE
                ) {
                    navigateFromAddress()
                    true
                } else {
                    false
                }
            }
        }

        searchContainer.addView(
            address,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        root.addView(
            searchContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                setMargins(
                    dp(12),
                    dp(4),
                    dp(12),
                    dp(8)
                )
            }
        )

        // WEBSITE DISPLAY AREA

        pageHost = FrameLayout(this)

        root.addView(
            pageHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // NO BOTTOM BROWSER NAVIGATION
        // WEBSITES NOW USE THE AVAILABLE SCREEN SPACE.

        setContentView(root)

        updateTabCount()
    }

    private fun button(
        label: String,
        parent: LinearLayout,
        action: () -> Unit
    ): TextView {

        val v = TextView(this).apply {

            text = label

            textSize = 23f

            gravity = Gravity.CENTER

            setTextColor(white)

            minWidth = dp(38)

            setPadding(
                dp(5),
                dp(5),
                dp(5),
                dp(5)
            )

            setOnClickListener {
                action()
            }
        }

        parent.addView(v)

        return v
    }

    // ==========================================
    // CUSTOM JOINT HOMEPAGE
    // ==========================================

    private fun showHomePage() {

        pageHost.removeAllViews()

        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dp(20),
                dp(25),
                dp(20),
                dp(15)
            )
            setBackgroundColor(dark)
        }

        home.addView(
            View(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.joint_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        home.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(105),
                dp(105)
            )
        )

        val title = TextView(this).apply {
            text = "JOINT Browser"
            textSize = 25f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        }

        home.addView(title)

        val subtitle = TextView(this).apply {
            text = "Browse. Discover. Connect."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        home.addView(subtitle)

        val google = TextView(this).apply {
            text = "Google Search"
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, dp(25), 0, dp(10))
        }

        home.addView(google)

        // OPEN JOINT

        val jointButton = TextView(this).apply {

            text = "J     Open JOINT"

            textSize = 17f

            setTextColor(white)

            setTypeface(null, Typeface.BOLD)

            gravity = Gravity.CENTER

            background = rounded(purple, 30)

            setPadding(
                dp(20),
                dp(15),
                dp(20),
                dp(15)
            )

            setOnClickListener {
                openWebsite(jointUrl)
            }
        }

        home.addView(
            jointButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        // ADMOB SPACE

        val adSpace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(
                Color.rgb(35, 36, 47),
                12
            )
            setPadding(
                dp(10),
                dp(10),
                dp(10),
                dp(10)
            )
        }

        val adTitle = TextView(this).apply {
            text = "ADVERTISEMENT"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val adText = TextView(this).apply {
            text = "AdMob Banner Space"
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
        }

        adSpace.addView(adTitle)
        adSpace.addView(adText)

        home.addView(
            adSpace,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(110)
            ).apply {
                setMargins(
                    0,
                    dp(35),
                    0,
                    dp(10)
                )
            }
        )

        val footer = TextView(this).apply {
            text = "Powered by JOINT"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        home.addView(footer)

        home.addView(
            View(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        pageHost.addView(
            home,
            FrameLayout.LayoutParams(-1, -1)
        )

        address.setText("")
        address.hint = "Search Google or enter website"

        updateTabCount()
    }

    // ==========================================
    // WEBVIEW AND TABS
    // ==========================================

    private fun addTab(
        url: String,
        privateMode: Boolean
    ) {

        val web = WebView(this)

        web.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically = false

            setSupportMultipleWindows(false)

            allowFileAccess = false

            allowContentAccess = false

            mediaPlaybackRequiresUserGesture = true

            if (privateMode) {
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }

        if (privateMode) {
            web.clearCache(true)
            web.clearHistory()
        }

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val uri = request.url

                if (
                    uri.scheme == "http" ||
                    uri.scheme == "https"
                ) {
                    return false
                }

                return try {

                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            uri
                        )
                    )

                    true

                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {

                if (tabs.getOrNull(active)?.web === view) {

                    address.setText(
                        cleanAddress(url)
                    )
                }
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {

                if (tabs.getOrNull(active)?.web === view) {

                    address.setText(
                        cleanAddress(url)
                    )
                }
            }
        }

        web.webChromeClient = WebChromeClient()

        web.setDownloadListener(
            DownloadListener {
                    downloadUrl,
                    userAgent,
                    disposition,
                    mimeType,
                    _ ->

                downloadFile(
                    downloadUrl,
                    userAgent,
                    disposition,
                    mimeType
                )
            }
        )

        tabs.add(
            BrowserTab(web, privateMode)
        )

        active = tabs.lastIndex

        showHomePage()

        updateTabCount()

        if (url.isNotBlank()) {
            openWebsite(url)
        }
    }

    private fun cleanAddress(url: String?): String {

        return url
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.trimEnd('/')
            ?: ""
    }

    private fun currentWeb(): WebView? {

        return if (tabs.isEmpty()) {
            null
        } else {
            tabs[active].web
        }
    }

    // ==========================================
    // SEARCH AND WEBSITE NAVIGATION
    // ==========================================

    private fun navigateFromAddress() {

        val input = address.text.toString().trim()

        if (input.isEmpty()) return

        val target = if (
            input.startsWith("http://", true) ||
            input.startsWith("https://", true)
        ) {

            input

        } else if (
            input.startsWith("www.", true) ||
            (
                input.contains(".") &&
                !input.contains(" ")
            )
        ) {

            "https://$input"

        } else {

            "https://www.google.com/search?q=" +
                URLEncoder.encode(input, "UTF-8")
        }

        openWebsite(target)

        val manager = getSystemService(
            INPUT_METHOD_SERVICE
        ) as InputMethodManager

        manager.hideSoftInputFromWindow(
            address.windowToken,
            0
        )
    }

    private fun openWebsite(url: String) {

        val tab = tabs.getOrNull(active) ?: return

        tab.showingHome = false

        pageHost.removeAllViews()

        pageHost.addView(
            tab.web,
            FrameLayout.LayoutParams(-1, -1)
        )

        address.hint = "Search Google or enter website"

        tab.web.loadUrl(url)

        updateTabCount()
    }

    // ==========================================
    // HOME AND REFRESH
    // ==========================================

    private fun showHome() {

        val tab = tabs.getOrNull(active) ?: return

        tab.showingHome = true

        showHomePage()
    }

    private fun refreshPage() {

        val tab = tabs.getOrNull(active) ?: return

        if (tab.showingHome) {

            showHomePage()

        } else {

            tab.web.reload()
        }
    }

    // ==========================================
    // BACK NAVIGATION
    // ==========================================

    private fun goBack() {

        val tab = tabs.getOrNull(active) ?: return

        if (!tab.showingHome && tab.web.canGoBack()) {

            tab.web.goBack()

        } else {

            showHome()
        }
    }

    override fun onBackPressed() {
        goBack()
    }

    // ==========================================
    // TAB MANAGEMENT
    // ==========================================

    private fun updateTabCount() {

        tabCounter.text = tabs.size.toString()

        tabCounter.contentDescription =
            "${tabs.size} open tabs"
    }

    private fun showTabs() {

        if (tabs.isEmpty()) return

        val names = tabs.mapIndexed { i, tab ->

            "${if (i == active) "✓ " else ""}" +
                "${if (tab.privateMode) "Private" else "Tab"} ${i + 1} — " +
                "${
                    if (tab.showingHome) {
                        "JOINT Homepage"
                    } else {
                        tab.web.title
                            ?: tab.web.url
                            ?: "Website"
                    }
                }"

        }.toMutableList()

        names.add("＋ New tab")

        AlertDialog.Builder(this)

            .setTitle("Browser Tabs (${tabs.size})")

            .setItems(
                names.toTypedArray()
            ) { _, which ->

                if (which == tabs.size) {

                    addTab("", false)

                } else {

                    switchTab(which)
                }
            }

            .setNeutralButton("Close current") { _, _ ->

                closeCurrentTab()
            }

            .show()
    }

    private fun switchTab(index: Int) {

        if (index !in tabs.indices) return

        active = index

        val tab = tabs[active]

        if (tab.showingHome) {

            showHomePage()

        } else {

            pageHost.removeAllViews()

            pageHost.addView(
                tab.web,
                FrameLayout.LayoutParams(-1, -1)
            )

            address.setText(
                cleanAddress(tab.web.url)
            )
        }

        updateTabCount()
    }

    private fun closeCurrentTab() {

        if (tabs.isEmpty()) return

        val old = tabs.removeAt(active)

        old.web.stopLoading()

        old.web.destroy()

        if (tabs.isEmpty()) {

            addTab("", false)

        } else {

            active = (active - 1).coerceAtLeast(0)

            switchTab(active)
        }

        updateTabCount()
    }

    // ==========================================
    // BROWSER MENU
    // ==========================================

    private fun showMenu() {

        val items = arrayOf(
            "Downloads",
            "Bookmark this page",
            "Bookmarks",
            "Open private tab",
            "Add page to home screen",
            "Install website in Chrome",
            "Copy page link",
            "Open JOINT website"
        )

        AlertDialog.Builder(this)

            .setTitle("JOINT Browser")

            .setItems(items) { _, which ->

                when (which) {

                    0 -> openDownloads()

                    1 -> saveBookmark()

                    2 -> showBookmarks()

                    3 -> addTab("", true)

                    4 -> pinCurrentPage()

                    5 -> installWebsiteInChrome()

                    6 -> copyPageLink()

                    7 -> openWebsite(jointUrl)
                }
            }

            .show()
    }

    // ==========================================
    // INSTALL WEBSITE USING CHROME
    // ==========================================

    private fun installWebsiteInChrome() {

        val url = currentWeb()?.url ?: jointUrl

        try {

            val chromeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ).apply {

                setPackage("com.android.chrome")
            }

            startActivity(chromeIntent)

            Toast.makeText(
                this,
                "Open Chrome's menu to install this website if supported",
                Toast.LENGTH_LONG
            ).show()

        } catch (_: Exception) {

            try {

                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )

                startActivity(
                    Intent.createChooser(
                        browserIntent,
                        "Open website with"
                    )
                )

            } catch (_: Exception) {

                Toast.makeText(
                    this,
                    "Unable to open Chrome",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ==========================================
    // DOWNLOADS
    // ==========================================

    private fun downloadFile(
        url: String,
        userAgent: String,
        disposition: String,
        mime: String
    ) {

        try {

            val request = DownloadManager.Request(
                Uri.parse(url)
            )

            val filename = URLUtil.guessFileName(
                url,
                disposition,
                mime
            )

            request.setTitle(filename)

            request.setDescription(
                "Downloading with JOINT Browser"
            )

            request.setMimeType(mime)

            request.addRequestHeader(
                "User-Agent",
                userAgent
            )

            CookieManager.getInstance()
                .getCookie(url)
                ?.let {
                    request.addRequestHeader(
                        "Cookie",
                        it
                    )
                }

            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                filename
            )

            val manager = getSystemService(
                DOWNLOAD_SERVICE
            ) as DownloadManager

            manager.enqueue(request)

            Toast.makeText(
                this,
                "Download started: $filename",
                Toast.LENGTH_LONG
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Unable to download this file",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openDownloads() {

        try {

            startActivity(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            )

        } catch (_: Exception) {

            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // ==========================================
    // BOOKMARKS
    // ==========================================

    private fun saveBookmark() {

        val url = currentWeb()?.url ?: return

        val title = currentWeb()?.title ?: url

        val prefs = getSharedPreferences(
            "joint_browser",
            MODE_PRIVATE
        )

        val old = prefs.getStringSet(
            "bookmarks",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        old.add("$title|$url")

        prefs.edit()
            .putStringSet("bookmarks", old)
            .apply()

        Toast.makeText(
            this,
            "Bookmark saved",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {

        val saved = getSharedPreferences(
            "joint_browser",
            MODE_PRIVATE
        ).getStringSet(
            "bookmarks",
            emptySet()
        )?.toList() ?: emptyList()

        if (saved.isEmpty()) {

            Toast.makeText(
                this,
                "No bookmarks yet",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(this)

            .setTitle("Bookmarks")

            .setItems(
                saved.map {
                    it.substringAfter("|", it)
                }.toTypedArray()
            ) { _, which ->

                openWebsite(
                    saved[which].substringAfter("|")
                )
            }

            .setNegativeButton("Close", null)

            .show()
    }

    // ==========================================
    // ADD WEBSITE TO ANDROID HOME SCREEN
    // ==========================================

    private fun pinCurrentPage() {

        val tab = tabs.getOrNull(active) ?: return

        val url = tab.web.url ?: if (tab.showingHome) {
            jointUrl
        } else {
            return
        }

        val title = (
            tab.web.title
                ?: if (tab.showingHome) "JOINT" else "Website"
            ).take(25)

        if (Build.VERSION.SDK_INT >= 26) {

            val manager = getSystemService(
                ShortcutManager::class.java
            )

            if (manager.isRequestPinShortcutSupported) {

                val shortcutIntent = Intent(
                    this,
                    MainActivity::class.java
                ).apply {

                    putExtra("shortcut_url", url)
                }

                val shortcutIcon = tab.web.favicon?.let {
                    Icon.createWithBitmap(it)
                } ?: Icon.createWithResource(
                    this,
                    R.drawable.joint_logo
                )

                val shortcut = ShortcutInfo.Builder(
                    this,
                    "page_${System.currentTimeMillis()}"
                )

                    .setShortLabel(title)

                    .setLongLabel(title)

                    .setIcon(shortcutIcon)

                    .setIntent(shortcutIntent)

                    .build()

                manager.requestPinShortcut(
                    shortcut,
                    null
                )

            } else {

                Toast.makeText(
                    this,
                    "Launcher does not support shortcuts",
                    Toast.LENGTH_LONG
                ).show()
            }

        } else {

            Toast.makeText(
                this,
                "Shortcuts require Android 8 or newer",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ==========================================
    // COPY LINK
    // ==========================================

    private fun copyPageLink() {

        val url = currentWeb()?.url ?: return

        val clipboard = getSystemService(
            CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Website",
                url
            )
        )

        Toast.makeText(
            this,
            "Link copied",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ==========================================
    // CLEANUP
    // ==========================================

    override fun onDestroy() {

        tabs.forEach {

            it.web.stopLoading()

            it.web.destroy()
        }

        tabs.clear()

        super.onDestroy()
    }

    private fun dp(value: Int): Int {

        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
