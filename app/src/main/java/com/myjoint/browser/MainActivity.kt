package com.myjoint.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.content.ClipboardManager
import android.content.ClipData
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import java.net.URLEncoder

class MainActivity : Activity() {
    private data class BrowserTab(val web: WebView, val privateMode: Boolean)
    private val tabs = mutableListOf<BrowserTab>()
    private var active = 0
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var pageHost: FrameLayout
    private lateinit var tabLabel: TextView
    private var dark = false

    private val homeUrl = "https://www.google.com"
    private val jointUrl = "https://myjointapp.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        addTab(homeUrl, false)
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 19, 25))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        address = EditText(this).apply {
            hint = "Search Google or enter website"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(42, 47, 56))
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigateFromAddress()
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))

        val newTab = button("+", top) { addTab(homeUrl, false) }
        val tabsButton = button("▢", top) { showTabs() }
        button("⋮", top) { showMenu() }
        newTab.contentDescription = "New tab"
        tabsButton.contentDescription = "Tabs"

        root.addView(top)

        tabLabel = TextView(this).apply {
            text = "JOINT Browser"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(dp(14), dp(2), dp(14), dp(6))
        }
        root.addView(tabLabel)

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setBackgroundColor(Color.rgb(27, 31, 39))
        }
        button("‹", bottom) { goBack() }
        button("›", bottom) { currentWeb()?.let { if (it.canGoForward()) it.goForward() } }
        button("⌂", bottom) { showHome() }
        button("↻", bottom) { currentWeb()?.reload() }
        root.addView(bottom)
        setContentView(root)
    }

    private fun button(label: String, parent: LinearLayout, action: () -> Unit): TextView {
        val v = TextView(this).apply {
            text = label
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minWidth = dp(42)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { action() }
        }
        parent.addView(v)
        return v
    }

    private fun addTab(url: String, privateMode: Boolean) {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.mediaPlaybackRequiresUserGesture = true
        if (privateMode) {
            web.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            web.clearCache(true)
            web.clearHistory()
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "http" || uri.scheme == "https") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri)); true
                } catch (_: Exception) { true }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                address.setText(url ?: "")
                tabLabel.text = if (privateMode) "Private tab" else "JOINT Browser"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                address.setText(url ?: "")
            }
        }
        web.webChromeClient = WebChromeClient()
        web.setDownloadListener(DownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(downloadUrl, userAgent, contentDisposition, mimeType)
        })

        tabs.add(BrowserTab(web, privateMode))
        active = tabs.lastIndex
        pageHost.removeAllViews()
        pageHost.addView(web, FrameLayout.LayoutParams(-1, -1))
        if (url.isNotBlank()) web.loadUrl(url)
        updateTabCount()
    }

    private fun currentWeb(): WebView? = if (tabs.isEmpty()) null else tabs[active].web

    private fun navigateFromAddress() {
        val input = address.text.toString().trim()
        if (input.isEmpty()) return
        val target = if (input.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) ||
            input.startsWith("www.", true) ||
            (input.contains(".") && !input.contains(" "))) {
            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        } else {
            "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
        }
        currentWeb()?.loadUrl(target)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
    }

    private fun showHome() {
        val web = currentWeb() ?: return
        web.loadUrl(homeUrl)
        address.setText(homeUrl)
    }

    private fun goBack() {
        val web = currentWeb() ?: return
        if (web.canGoBack()) web.goBack() else showHome()
    }

    private fun showTabs() {
        if (tabs.isEmpty()) return
        val names = tabs.mapIndexed { i, tab ->
            "${if (i == active) "✓ " else ""}${if (tab.privateMode) "Private" else "Tab"} ${i + 1} — ${tab.web.title ?: tab.web.url ?: "New tab"}"
        }.toMutableList()
        names.add("＋  New tab")
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == tabs.size) addTab(homeUrl, false) else switchTab(which)
            }
            .setNeutralButton("Close current") { _, _ -> closeCurrentTab() }
            .show()
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        active = index
        pageHost.removeAllViews()
        pageHost.addView(tabs[active].web, FrameLayout.LayoutParams(-1, -1))
        address.setText(tabs[active].web.url ?: "")
        updateTabCount()
    }

    private fun closeCurrentTab() {
        if (tabs.isEmpty()) return
        val old = tabs.removeAt(active)
        old.web.stopLoading()
        old.web.destroy()
        if (tabs.isEmpty()) {
            addTab(homeUrl, false)
        } else {
            active = (active - 1).coerceAtLeast(0)
            switchTab(active)
        }
    }

    private fun updateTabCount() {
        tabLabel.text = "JOINT Browser  •  ${tabs.size} tab(s)"
    }

    private fun showMenu() {
        val items = arrayOf(
            "Downloads",
            "Bookmark this page",
            "Bookmarks",
            "Open private tab",
            "Add page to home screen",
            "Copy page link",
            "Settings"
        )
        AlertDialog.Builder(this).setTitle("JOINT Browser").setItems(items) { _, which ->
            when (which) {
                0 -> openDownloads()
                1 -> saveBookmark()
                2 -> showBookmarks()
                3 -> addTab(homeUrl, true)
                4 -> pinCurrentPage()
                5 -> copyPageLink()
                6 -> showSettings()
            }
        }.show()
    }

    private fun downloadFile(url: String, userAgent: String, disposition: String, mime: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val filename = android.webkit.URLUtil.guessFileName(url, disposition, mime)
            request.setTitle(filename)
            request.setDescription("Downloading with JOINT Browser")
            request.setMimeType(mime)
            request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download started: $filename", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to download this file", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }

    private fun saveBookmark() {
        val url = currentWeb()?.url ?: return
        val title = currentWeb()?.title ?: url
        val prefs = getSharedPreferences("joint_browser", MODE_PRIVATE)
        val old = prefs.getStringSet("bookmarks", emptySet())?.toMutableSet() ?: mutableSetOf()
        old.add("$title|$url")
        prefs.edit().putStringSet("bookmarks", old).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val saved = getSharedPreferences("joint_browser", MODE_PRIVATE)
            .getStringSet("bookmarks", emptySet())?.toList() ?: emptyList()
        if (saved.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Bookmarks")
            .setItems(saved.map { it.substringAfter("|", it) }.toTypedArray()) { _, which ->
                currentWeb()?.loadUrl(saved[which].substringAfter("|"))
            }.setNegativeButton("Close", null).show()
    }

    private fun pinCurrentPage() {
        val url = currentWeb()?.url ?: return
        val title = currentWeb()?.title?.take(25) ?: "Website"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(ShortcutManager::class.java)
            if (manager.isRequestPinShortcutSupported) {
                val shortcut = ShortcutInfo.Builder(this, "page_${System.currentTimeMillis()}")
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_view))
                    .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this, MainActivity::class.java))
                    .build()
                manager.requestPinShortcut(shortcut, null)
            } else Toast.makeText(this, "Your launcher does not support pinned shortcuts", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Home-screen shortcuts require Android 8 or newer", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyPageLink() {
        val url = currentWeb()?.url ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Website", url))
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun showSettings() {
        val options = arrayOf("Dark appearance", "Light appearance", "Open JOINT website")
        AlertDialog.Builder(this).setTitle("Settings").setItems(options) { _, which ->
            when (which) {
                0 -> { dark = true; Toast.makeText(this, "Dark browser appearance selected", Toast.LENGTH_SHORT).show() }
                1 -> { dark = false; Toast.makeText(this, "Light browser appearance selected", Toast.LENGTH_SHORT).show() }
                2 -> currentWeb()?.loadUrl(jointUrl)
            }
        }.show()
    }

    override fun onBackPressed() {
        goBack()
    }

    override fun onDestroy() {
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
