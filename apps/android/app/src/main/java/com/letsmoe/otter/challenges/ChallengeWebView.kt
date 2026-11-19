package com.letsmoe.otter.challenges

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Cookies a WebView earned for a site, and the user agent they are bound to. */
data class Clearance(val cookies: String, val userAgent: String)

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

/** A WebView that runs a site's check like a browser: JavaScript, storage and cookies on. */
@SuppressLint("SetJavaScriptEnabled")
fun createChallengeWebView(context: Context): WebView {
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.webViewClient = WebViewClient()
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    return webView
}

/** Lays out a WebView that is never attached to a window, so its page renders and runs. */
fun layOutOffscreen(webView: WebView) {
    val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, android.view.View.MeasureSpec.EXACTLY)
    val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, android.view.View.MeasureSpec.EXACTLY)
    webView.measure(widthSpec, heightSpec)
    webView.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
}

/** Waits until the page in [webView] is past the site's check, then returns its cookies; never returns while the check stays. */
suspend fun awaitClearance(webView: WebView, url: String): Clearance {
    while (true) {
        delay(POLL_MILLISECONDS)
        val clearance = readClearance(webView, url)
        if (clearance != null) {
            return clearance
        }
    }
}

// Null while the page loads or still shows the check.
private suspend fun readClearance(webView: WebView, url: String): Clearance? {
    if (webView.progress < 100) {
        return null
    }
    if (showsCheck(webView)) {
        return null
    }
    val cookies = CookieManager.getInstance().getCookie(url)
    if (cookies.isNullOrEmpty()) {
        return null
    }
    return Clearance(cookies, webView.settings.userAgentString)
}

private suspend fun showsCheck(webView: WebView): Boolean = suspendCancellableCoroutine { continuation ->
    webView.evaluateJavascript(SHOWS_CHECK_SCRIPT) { result ->
        continuation.resume(result == "true")
    }
}
