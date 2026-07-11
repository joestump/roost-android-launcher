package rocks.stump.roost

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Opens a user-added web app (self-hosted dashboard, etc.) fullscreen in a WebView — so a URL from
 * Settings behaves like an installed app. Launched with an [EXTRA_URL] string extra.
 */
class WebAppActivity : Activity() {

    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val w = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Roost.DOCK)
            webViewClient = WebViewClient() // keep navigation inside the web app
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
        web = w
        setContentView(w)
        w.loadUrl(url)
    }

    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
