package com.letsmoe.otter.challenges

import android.content.Context
import android.util.Log
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.ServerSettings
import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.graphql.ChallengesSubscription
import com.letsmoe.otter.graphql.type.ChallengeKind
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

/**
 * A site's bot check the backend asks the app to pass. A [navigation] also wants the first URL
 * off the site, e.g. the hoster a captcha-gated link leads to: [click] names the element to click
 * once the page has loaded, [submit] the form to send once its captchas are solved.
 */
data class SiteChallenge(
    val id: String,
    val url: String,
    val domain: String,
    val interactive: Boolean,
    val navigation: Boolean,
    val click: String? = null,
    val submit: String? = null,
)

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
    // Under the backend's 25 s, so it hears "failed" rather than timing out.
    private const val HIDDEN_TIMEOUT_MILLISECONDS = 20_000L

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
                    val navigation = challenge.kind == ChallengeKind.NAVIGATION
                    onChallenge(
                        SiteChallenge(challenge.id, challenge.url, challenge.domain, challenge.interactive, navigation, challenge.click, challenge.submit),
                    )
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
        try {
            val result = hiddenAttempts.withLock { passHidden(challenge) }
            if (result == null) {
                fail(challenge.id, "The check wants a click.")
                return
            }
            Log.i(TAG, "passed the check of ${challenge.domain} hidden")
            complete(challenge.id, result)
        } catch (gone: LinkGoneException) {
            fail(challenge.id, gone.message.orEmpty(), linkGone = true)
        }
    }

    private suspend fun passHidden(challenge: SiteChallenge): ChallengeResult? = withContext(Dispatchers.Main) {
        val page = ChallengePage(applicationContext, challenge)
        page.layOutOffscreen()
        try {
            page.load()
            withTimeoutOrNull(HIDDEN_TIMEOUT_MILLISECONDS) { page.await() }
        } finally {
            page.destroy()
        }
    }

    /** The sheet's WebView passed the check. */
    fun onShownSolved(challenge: SiteChallenge, result: ChallengeResult) {
        dismiss(challenge)
        AppScope.launch { complete(challenge.id, result) }
    }

    /** The sheet's page doesn't have the link any more. */
    fun onShownLinkGone(challenge: SiteChallenge, reason: String) {
        dismiss(challenge)
        AppScope.launch { fail(challenge.id, reason, linkGone = true) }
    }

    /** The user closed the sheet without passing the check. */
    fun onShownDismissed(challenge: SiteChallenge) {
        dismiss(challenge)
        AppScope.launch { fail(challenge.id, "Closed by the user.") }
    }

    private fun dismiss(challenge: SiteChallenge) {
        mutableShown.update { waiting -> waiting.filter { candidate -> candidate.id != challenge.id } }
    }

    private suspend fun complete(id: String, result: ChallengeResult) {
        val mutation = CompleteChallengeMutation(id, result.cookies, result.userAgent, Optional.presentIfNotNull(result.finalUrl))
        send("completing") { OtterApi.client.mutation(mutation).execute() }
    }

    private suspend fun fail(id: String, reason: String, linkGone: Boolean = false) {
        send("failing") { OtterApi.client.mutation(FailChallengeMutation(id, reason, Optional.present(linkGone))).execute() }
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
