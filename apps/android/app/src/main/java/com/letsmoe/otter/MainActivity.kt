package com.letsmoe.otter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.letsmoe.otter.links.IncomingLinks
import com.letsmoe.otter.neoworks.NeoworksAccountStore
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.together.roomCodeFromInviteLink
import com.letsmoe.otter.ui.OtterApp
import com.letsmoe.otter.ui.splash.SplashOverlay
import com.letsmoe.otter.ui.splash.SplashPlayback

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferFastestRefreshRate()
        setContent {
            Box(Modifier.fillMaxSize()) {
                if (SplashPlayback.appMayCompose || !SplashPlayback.shouldPlay()) {
                    OtterApp()
                }
                SplashOverlay()
            }
        }
        openLink(intent)
    }

    // Back in front: pick up what other devices changed meanwhile.
    override fun onStart() {
        super.onStart()
        NeoworksAccountStore.syncSoon()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        if (NeoworksAccountStore.handleCallback(intent)) {
            return
        }
        if (IncomingLinks.handle(intent)) {
            return
        }
        openInvite(intent)
    }

    // ColorOS keeps apps it doesn't know at 60 Hz; ask for the panel's fastest mode at our resolution.
    private fun preferFastestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val currentDisplay = display ?: return
        val currentMode = currentDisplay.mode
        val fastest = currentDisplay.supportedModes
            .filter { mode -> mode.physicalWidth == currentMode.physicalWidth && mode.physicalHeight == currentMode.physicalHeight }
            .maxByOrNull { mode -> mode.refreshRate }
        if (fastest == null) {
            return
        }
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }

    // Invite links (otter.letsmoe.com/together/CODE) open the Watch Together sheet.
    private fun openInvite(intent: Intent?) {
        val link = intent?.dataString ?: return
        val code = roomCodeFromInviteLink(link) ?: return
        TogetherSession.openSheet(code)
    }
}
