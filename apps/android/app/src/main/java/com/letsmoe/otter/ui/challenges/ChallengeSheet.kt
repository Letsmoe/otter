package com.letsmoe.otter.ui.challenges

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.challenges.ChallengePage
import com.letsmoe.otter.challenges.ChallengeSolver
import com.letsmoe.otter.challenges.LinkGoneException
import com.letsmoe.otter.challenges.SiteChallenge
import com.letsmoe.otter.ui.components.GlassBottomSheet
import kotlinx.coroutines.delay

private const val ISOLATE_POLL_MILLISECONDS = 400L
// A page whose captcha never shows up as a known widget is shown whole after this long.
private const val SHOW_WHOLE_PAGE_AFTER_MILLISECONDS = 6_000L

/** The site's check the app couldn't pass hidden, for the user to tap through; closes itself once passed. */
@Composable
fun ChallengeSheetHost() {
    val waiting by ChallengeSolver.shown.collectAsStateWithLifecycle()
    val challenge = waiting.firstOrNull() ?: return
    key(challenge.id) {
        ChallengeSheet(challenge)
    }
}

@Composable
private fun ChallengeSheet(challenge: SiteChallenge) {
    val context = LocalContext.current
    val page = remember { ChallengePage(context, challenge) }
    var captchaVisible by remember { mutableStateOf(false) }
    DisposableEffect(page) {
        page.load()
        onDispose { page.destroy() }
    }
    LaunchedEffect(page) {
        try {
            ChallengeSolver.onShownSolved(challenge, page.await())
        } catch (gone: LinkGoneException) {
            ChallengeSolver.onShownLinkGone(challenge, gone.message.orEmpty())
        }
    }
    LaunchedEffect(page) {
        captchaVisible = showOnlyTheCaptcha(page)
    }
    GlassBottomSheet(onDismiss = { ChallengeSolver.onShownDismissed(challenge) }, fullHeight = true) {
        ChallengeSheetContent(challenge, page.webView, captchaVisible)
    }
}

// Keeps hiding everything but the captcha (the page may redraw); true once it shows, or once it's time to show the page whole.
private suspend fun showOnlyTheCaptcha(page: ChallengePage): Boolean {
    var waited = 0L
    while (waited < SHOW_WHOLE_PAGE_AFTER_MILLISECONDS) {
        if (page.isolateCaptcha()) {
            return true
        }
        delay(ISOLATE_POLL_MILLISECONDS)
        waited += ISOLATE_POLL_MILLISECONDS
    }
    return true
}

// The sheet's drag-to-close would otherwise take vertical swipes from the page after a few pixels.
@SuppressLint("ClickableViewAccessibility")
private fun keepingItsTouches(webView: WebView): WebView {
    webView.setOnTouchListener { view, _ ->
        view.parent?.requestDisallowInterceptTouchEvent(true)
        false
    }
    return webView
}

@Composable
private fun ChallengeSheetContent(challenge: SiteChallenge, webView: WebView, captchaVisible: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confirm you're human", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "${challenge.domain} checks visitors before showing its links. Tap the box below; this closes on its own once it lets you through.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            var webViewAlpha = 0f
            if (captchaVisible) {
                webViewAlpha = 1f
            }
            AndroidView(
                factory = { keepingItsTouches(webView) },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).alpha(webViewAlpha),
            )
            if (!captchaVisible) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
            }
        }
    }
}
