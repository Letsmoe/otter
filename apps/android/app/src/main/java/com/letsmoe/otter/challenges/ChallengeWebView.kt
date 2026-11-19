package com.letsmoe.otter.challenges

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** What a WebView brought back: the site's cookies, the user agent they are bound to, and where a navigation led. */
data class ChallengeResult(val cookies: String, val userAgent: String, val finalUrl: String? = null)

/** The page doesn't have the element to click: the link is dead, not waiting for a captcha. */
class LinkGoneException(message: String) : Exception(message)

private const val POLL_MILLISECONDS = 500L
// How long a loaded page may lack the element to click before the link counts as gone.
private const val MISSING_CLICK_MILLISECONDS = 6_000L
// A phone-sized viewport: some checks only run on a page that has a layout.
private const val VIEWPORT_WIDTH = 1080
private const val VIEWPORT_HEIGHT = 1920
private const val CLOUDFLARE_CHALLENGES = "challenges.cloudflare.com"

/**
 * One challenge's page in a WebView that behaves like a browser (JavaScript, storage, cookies).
 *
 * A clearance ends when the page is past the site's check. A navigation clicks [SiteChallenge.click],
 * then ends at the first page off the site: the whole page leaving, or, with [SiteChallenge.submit],
 * any frame loading an off-site page after that form was posted (s.to opens the hoster in its player
 * iframe). That page isn't loaded: only its URL is wanted.
 */
@SuppressLint("SetJavaScriptEnabled")
class ChallengePage(context: Context, private val challenge: SiteChallenge) {

    val webView = WebView(context)
    private val leftSite = CompletableDeferred<String>()
    // Set from WebView's network thread once the form was posted, by us or by the page itself.
    @Volatile private var formPosted = false
    private var formAction: String? = null
    private var clicked = false
    private var loadedAtMilliseconds: Long? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WatchingClient()
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

    /** Suspends until the check is passed (and, for a navigation, the page led off the site); throws [LinkGoneException]. */
    suspend fun await(): ChallengeResult {
        if (challenge.navigation) {
            return awaitNavigation()
        }
        return awaitClearance()
    }

    /** Shows only the captcha on the page; false while it has none yet. */
    suspend fun isolateCaptcha(): Boolean {
        return evaluate(ISOLATE_CAPTCHA_SCRIPT) == "isolated"
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun awaitNavigation(): ChallengeResult {
        while (!leftSite.isCompleted) {
            delay(POLL_MILLISECONDS)
            stepThroughPage()
        }
        return ChallengeResult(cookiesOfSite(), webView.settings.userAgentString, leftSite.await())
    }

    // Clicks the link once the page is past any check, then submits the captcha form once it's solved.
    private suspend fun stepThroughPage() {
        if (webView.progress < 100 || showsCheck()) {
            return
        }
        if (loadedAtMilliseconds == null) {
            loadedAtMilliseconds = SystemClock.elapsedRealtime()
        }
        if (!clicked) {
            clickLink()
            return
        }
        submitWhenSolved()
    }

    private suspend fun clickLink() {
        val selector = challenge.click
        if (selector == null) {
            clicked = true
            return
        }
        if (evaluate(clickScript(selector)) == "clicked") {
            clicked = true
            return
        }
        val loadedAt = loadedAtMilliseconds ?: return
        if (SystemClock.elapsedRealtime() - loadedAt > MISSING_CLICK_MILLISECONDS) {
            throw LinkGoneException("${challenge.domain} has no $selector on ${challenge.url}")
        }
    }

    private suspend fun submitWhenSolved() {
        val selector = challenge.submit ?: return
        if (formPosted) {
            return
        }
        if (formAction == null) {
            formAction = evaluate("(function(s){var f=document.querySelector(s);return f?f.action:'';})(${org.json.JSONObject.quote(selector)})")
        }
        evaluate(submitWhenSolvedScript(selector))
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

    private suspend fun showsCheck(): Boolean {
        return evaluate(SHOWS_CHECK_SCRIPT) == "true"
    }

    // The script's result without JSON quotes ("clicked", not "\"clicked\"").
    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            continuation.resume(result.orEmpty().removeSurrounding("\""))
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
        if (host == CLOUDFLARE_CHALLENGES) {
            return false
        }
        return host != challenge.domain && !host.endsWith("." + challenge.domain)
    }

    // A frame's page, as opposed to a script, image or XHR: browsers ask frames for HTML.
    private fun isPageRequest(request: WebResourceRequest): Boolean {
        val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"] ?: return false
        return accept.contains("text/html")
    }

    private fun isFormPost(request: WebResourceRequest): Boolean {
        val action = formAction
        if (request.method != "POST" || action.isNullOrEmpty()) {
            return false
        }
        return request.url.toString().substringBefore('?') == action.substringBefore('?')
    }

    private inner class WatchingClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return catchesLeaving(request.isForMainFrame, request.url.toString())
        }

        // Server redirects don't always pass shouldOverrideUrlLoading.
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (catchesLeaving(isMainFrame = true, url)) {
                view.stopLoading()
            }
        }

        // Runs on WebView's network thread for every request, frames included.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (isFormPost(request)) {
                formPosted = true
                return null
            }
            if (!formPosted || !isPageRequest(request) || !isOffSite(request.url.toString())) {
                return null
            }
            leftSite.complete(request.url.toString())
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
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
