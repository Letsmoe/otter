package com.letsmoe.otter.challenges

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What a WebView brought back: the site's cookies, the user agent they are bound to, and where a navigation led. */
data class ChallengeResult(val cookies: String, val userAgent: String, val finalUrl: String? = null)

private const val POLL_MILLISECONDS = 500L
// A phone-sized viewport: some checks only run on a page that has a layout.
private const val VIEWPORT_WIDTH = 1080
private const val VIEWPORT_HEIGHT = 1920

// Elements only Cloudflare's check pages have; normal pages may load its scripts too.
private const val SHOWS_CHECK_SCRIPT = """
(function() {
  var selectors = '#challenge-form, #challenge-running, #challenge-stage, #cf-challenge-running, .cf-turnstile, #turnstile-wrapper';
  return document.querySelector(selectors) !== null;
})()
"""

/**
 * One challenge's page in a WebView that behaves like a browser (JavaScript, storage, cookies).
 * A navigation ends at the first page off the site, which isn't loaded: only its URL is wanted.
 */
@SuppressLint("SetJavaScriptEnabled")
class ChallengePage(context: Context, private val challenge: SiteChallenge) {

    val webView = WebView(context)
    private val leftSite = CompletableDeferred<String>()

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = LeavingSiteClient()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    fun load() {
        webView.loadUrl(challenge.url)
    }

    /** Lays the page out without a window, so it renders and runs while hidden. */
    fun layOutOffscreen() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY)
        webView.measure(widthSpec, heightSpec)
        webView.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    }

    /** Suspends until the check is passed (and, for a navigation, the page left the site). */
    suspend fun await(): ChallengeResult {
        if (challenge.navigation) {
            val finalUrl = leftSite.await()
            return ChallengeResult(cookiesOfSite(), webView.settings.userAgentString, finalUrl)
        }
        return awaitClearance()
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun awaitClearance(): ChallengeResult {
        while (true) {
            delay(POLL_MILLISECONDS)
            val result = readClearance()
            if (result != null) {
                return result
            }
        }
    }

    // Null while the page loads or still shows the check.
    private suspend fun readClearance(): ChallengeResult? {
        if (webView.progress < 100) {
            return null
        }
        if (showsCheck()) {
            return null
        }
        val cookies = cookiesOfSite()
        if (cookies.isEmpty()) {
            return null
        }
        return ChallengeResult(cookies, webView.settings.userAgentString)
    }

    private suspend fun showsCheck(): Boolean = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(SHOWS_CHECK_SCRIPT) { result ->
            continuation.resume(result == "true")
        }
    }

    private fun cookiesOfSite(): String {
        return CookieManager.getInstance().getCookie(challenge.url).orEmpty()
    }

    private fun isOffSite(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            return false
        }
        val host = uri.host.orEmpty().removePrefix("www.")
        return host != challenge.domain && !host.endsWith("." + challenge.domain)
    }

    private inner class LeavingSiteClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return catchesLeaving(request.isForMainFrame, request.url.toString())
        }

        // Server redirects don't always pass shouldOverrideUrlLoading.
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (catchesLeaving(isMainFrame = true, url)) {
                view.stopLoading()
            }
        }

        private fun catchesLeaving(isMainFrame: Boolean, url: String): Boolean {
            if (!challenge.navigation || !isMainFrame || !isOffSite(url)) {
                return false
            }
            leftSite.complete(url)
            return true
        }
    }
}
