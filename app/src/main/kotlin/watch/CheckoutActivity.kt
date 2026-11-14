package watch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Shows the event page in an ordinary WebView and lets the checkout assistant fill it.
 * The assistant stops at payment / CAPTCHA / unanswerable questions and rings; the person finishes there.
 */
class CheckoutActivity : Activity() {
    companion object {
        const val EXTRA_URL = "url"
        private const val POLL_MS = 400L
        private const val RELOAD_MS = 20_000L

        fun intent(c: Context, url: String) =
            Intent(c, CheckoutActivity::class.java).putExtra(EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun isAllowed(url: String): Boolean = try {
            val u = Uri.parse(url)
            u.scheme == "https" && (u.host == "eventer.co.il" || (u.host ?: "").endsWith(".eventer.co.il"))
        } catch (e: Exception) {
            false
        }
    }

    lateinit var webView: WebView
        private set
    private lateinit var banner: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var profile: Profile? = null
    private var engine = ""
    private var notifiedPhase: String? = null
    private var lastReload = 0L
    private var nextReloadIn = RELOAD_MS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!isAllowed(url)) { finish(); return }

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        profile = ProfileStore.forContext(this).load()
        engine = assets.open("checkout.js").bufferedReader().use { it.readText() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        banner = Ui.text(this, "", 15f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12))
        }
        root.addView(banner, LinearLayout.LayoutParams(-1, -2))

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val wv = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.url.scheme != "https" // never follow intent:, market:, http: links

                override fun onPageFinished(view: WebView, url: String?) = onPage(url.orEmpty())

                override fun onReceivedSslError(view: WebView, h: SslErrorHandler, e: SslError) = h.cancel()
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (profile == null) setBanner("No buyer details saved – open the app and add them", Ui.AMBER, Ui.BG)
        else setBanner("Starting…", Ui.ACCENT, 0xFFFFFFFF.toInt())
        lastReload = SystemClock.elapsedRealtime()
        webView.loadUrl(url)
        handler.postDelayed(poll, POLL_MS)
    }

    private val payHost = Regex("payplus|pelecard|payme|paypal|cardcom|tranzila|meshulam|stripe|braintree|creditguard", RegexOption.IGNORE_CASE)

    private fun onPage(url: String) {
        when {
            // The buyer's details go ONLY into Eventer's own pages, never into anything else.
            isAllowed(url) -> inject()
            payHost.containsMatchIn(Uri.parse(url).host.orEmpty()) && profile != null ->
                onStatus(CheckoutStatus("payment", "Payment page – enter the card and finish"))
        }
    }

    private fun inject() {
        val p = profile ?: return
        webView.evaluateJavascript(CheckoutScript.build(engine, p), null)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (profile != null) webView.evaluateJavascript("window.__ew ? window.__ew.status() : null") { raw ->
                CheckoutStatus.fromWebView(raw)?.let(::onStatus)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun onStatus(s: CheckoutStatus) {
        when {
            s.phase == "payment" -> setBanner("Payment page – enter the card and finish", Ui.GREEN, Ui.BG)
            s.phase == "human" -> setBanner("Verification needs you", Ui.RED, 0xFFFFFFFF.toInt())
            s.phase == "needs-input" -> setBanner(s.detail.ifBlank { "A question needs you" }, Ui.AMBER, Ui.BG)
            s.phase == "waiting" -> setBanner("Waiting for tickets to open…", Ui.ACCENT, 0xFFFFFFFF.toInt())
            else -> setBanner(s.detail.ifBlank { "Working…" }, Ui.ACCENT, 0xFFFFFFFF.toInt())
        }
        if (s.needsHuman && notifiedPhase != s.phase) {
            notifiedPhase = s.phase
            Alarm(this).also { it.ensureChannels() }.raiseCheckout(s)
        }
        if (s.shouldReload) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReload >= nextReloadIn) {
                lastReload = now
                nextReloadIn = RELOAD_MS + Random.nextLong(-3_000, 3_000)
                webView.reload()
            }
        }
    }

    private fun setBanner(t: String, bg: Int, fg: Int) {
        banner.text = t
        banner.setTextColor(fg)
        banner.setBackgroundColor(bg)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) (webView.parent as? ViewGroup)?.removeView(webView).also { webView.destroy() }
        super.onDestroy()
    }
}
