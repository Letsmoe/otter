package com.letsmoe.otter.challenges

import android.content.Context
import android.util.Log
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.ServerSettings
import com.letsmoe.otter.graphql.ChallengesSubscription
import com.letsmoe.otter.graphql.CompleteChallengeMutation
import com.letsmoe.otter.graphql.FailChallengeMutation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A site's bot check the backend asks the app to pass. */
data class SiteChallenge(val id: String, val url: String, val domain: String, val interactive: Boolean)

/**
 * Passes sites' bot checks (Cloudflare, Turnstile) for the embedded backend, like FlareSolverr:
 * hidden in an offscreen WebView first; only checks the backend marks interactive (someone is
 * waiting to play and the hidden attempt failed) are shown, as a sheet.
 *
 * Only for the embedded backend: a clearance holds only from the IP that earned it, and the
 * WebView shares this app's network (and VPN) with it, while a NAS may leave elsewhere.
 */
object ChallengeSolver {

    private const val TAG = "OtterChallenges"
    private const val RECONNECT_MILLISECONDS = 5_000L
    // Under the backend's 15 s, so it hears "failed" rather than timing out.
    private const val HIDDEN_TIMEOUT_MILLISECONDS = 12_000L

    private val mutableShown = MutableStateFlow<List<SiteChallenge>>(emptyList())
    /** Checks waiting for the user, oldest first; the sheet shows the first. */
    val shown: StateFlow<List<SiteChallenge>> = mutableShown

    private lateinit var applicationContext: Context
    // One hidden WebView at a time.
    private val hiddenAttempts = Mutex()

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        if (!ServerSettings.usesEmbeddedBackend) {
            return
        }
        AppScope.launch { listen() }
    }

    // Reconnects for as long as the app runs: while it listens, the backend offers plugins that need solving.
    private suspend fun listen() {
        OtterApi.awaitBackend()
        while (true) {
            try {
                OtterApi.client.subscription(ChallengesSubscription()).toFlow().collect { response ->
                    val challenge = response.data?.challenges ?: return@collect
                    onChallenge(SiteChallenge(challenge.id, challenge.url, challenge.domain, challenge.interactive))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "challenge subscription ended", error)
            }
            delay(RECONNECT_MILLISECONDS)
        }
    }

    private fun onChallenge(challenge: SiteChallenge) {
        if (challenge.interactive) {
            mutableShown.update { waiting -> waiting + challenge }
            return
        }
        AppScope.launch { solveHidden(challenge) }
    }

    private suspend fun solveHidden(challenge: SiteChallenge) {
        val clearance = hiddenAttempts.withLock { clearHidden(challenge.url) }
        if (clearance == null) {
            fail(challenge.id, "The check wants a click.")
            return
        }
        Log.i(TAG, "passed the check of ${challenge.domain} hidden")
        complete(challenge.id, clearance)
    }

    private suspend fun clearHidden(url: String): Clearance? = withContext(Dispatchers.Main) {
        val webView = createChallengeWebView(applicationContext)
        layOutOffscreen(webView)
        try {
            webView.loadUrl(url)
            withTimeoutOrNull(HIDDEN_TIMEOUT_MILLISECONDS) { awaitClearance(webView, url) }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    /** The sheet's WebView passed the check. */
    fun onShownSolved(challenge: SiteChallenge, clearance: Clearance) {
        dismiss(challenge)
        AppScope.launch { complete(challenge.id, clearance) }
    }

    /** The user closed the sheet without passing the check. */
    fun onShownDismissed(challenge: SiteChallenge) {
        dismiss(challenge)
        AppScope.launch { fail(challenge.id, "Closed by the user.") }
    }

    private fun dismiss(challenge: SiteChallenge) {
        mutableShown.update { waiting -> waiting.filter { candidate -> candidate.id != challenge.id } }
    }

    private suspend fun complete(id: String, clearance: Clearance) {
        send("completing") { OtterApi.client.mutation(CompleteChallengeMutation(id, clearance.cookies, clearance.userAgent)).execute() }
    }

    private suspend fun fail(id: String, reason: String) {
        send("failing") { OtterApi.client.mutation(FailChallengeMutation(id, reason)).execute() }
    }

    // The backend times the check out by itself when an answer gets lost.
    private suspend fun send(what: String, call: suspend () -> Unit) {
        try {
            call()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "$what a challenge failed", error)
        }
    }
}
