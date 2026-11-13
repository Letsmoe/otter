package com.letsmoe.otter.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.neoworks.NeoworksAccountState
import com.letsmoe.otter.neoworks.NeoworksAccountStore
import com.letsmoe.otter.neoworks.WelcomeChoice
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.components.PillButton

/**
 * First start: sign in with Neoworks (sync) or continue as a guest. Shown over the profile
 * picker until answered; signing in later stays possible in Settings, Sync.
 */
@Composable
fun WelcomeOverlay() {
    LaunchedEffect(Unit) { NeoworksAccountStore.refresh() }
    val account by NeoworksAccountStore.state.collectAsStateWithLifecycle()
    LaunchedEffect(account.signedIn) {
        if (account.signedIn) {
            WelcomeChoice.markAnswered()
        }
    }
    if (!welcomeVisible()) {
        return
    }
    SetupCard {
        WelcomeContent(account)
    }
}

/** Also read by the TMDB key dialog, which waits until the welcome is answered. */
@Composable
fun welcomeVisible(): Boolean {
    val answered by WelcomeChoice.answered.collectAsStateWithLifecycle()
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val account by NeoworksAccountStore.state.collectAsStateWithLifecycle()
    return !answered && activeProfile == null && account.known && !account.signedIn
}

@Composable
private fun WelcomeContent(account: NeoworksAccountState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Welcome to Otter", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Sign in with Neoworks to keep your profiles in sync across your devices, end-to-end encrypted. " +
                    "Or continue as a guest; you can sign in later in Settings.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(signInLabel(account), Modifier.fillMaxWidth(), enabled = !account.busy, filled = true) {
                NeoworksAccountStore.startSignIn(context)
            }
            PillButton("Continue as guest", Modifier.fillMaxWidth(), enabled = !account.busy, filled = false, onClick = WelcomeChoice::markAnswered)
        }
    }
}

private fun signInLabel(account: NeoworksAccountState): String {
    if (account.busy) {
        return "Signing in…"
    }
    return "Sign in with Neoworks"
}
