package com.letsmoe.otter.ui.challenges

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.challenges.ChallengeSolver
import com.letsmoe.otter.challenges.SiteChallenge
import com.letsmoe.otter.challenges.awaitClearance
import com.letsmoe.otter.challenges.createChallengeWebView
import com.letsmoe.otter.ui.components.GlassBottomSheet

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
    val webView = remember { createChallengeWebView(context) }
    DisposableEffect(webView) {
        webView.loadUrl(challenge.url)
        onDispose { webView.destroy() }
    }
    LaunchedEffect(webView) {
        val clearance = awaitClearance(webView, challenge.url)
        ChallengeSolver.onShownSolved(challenge, clearance)
    }
    GlassBottomSheet(onDismiss = { ChallengeSolver.onShownDismissed(challenge) }, fullHeight = true) {
        ChallengeSheetContent(challenge, webView)
    }
}

@Composable
private fun ChallengeSheetContent(challenge: SiteChallenge, webView: WebView) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confirm you're human", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "${challenge.domain} checks visitors before showing its links. Tap the box below; this closes on its own once it lets you through.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(16.dp)),
        )
    }
}
