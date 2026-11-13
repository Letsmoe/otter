package com.letsmoe.otter.neoworks

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.CompleteNeoworksSignInMutation
import com.letsmoe.otter.graphql.NeoworksAccountQuery
import com.letsmoe.otter.graphql.SignOutOfNeoworksMutation
import com.letsmoe.otter.graphql.StartNeoworksSignInMutation
import com.letsmoe.otter.graphql.SyncNeoworksMutation
import com.letsmoe.otter.graphql.SyncNeoworksSoonMutation
import com.letsmoe.otter.graphql.fragment.NeoworksAccountFields
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.components.openWebPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Neoworks returns every sign-in here; App Links bring it to Otter (apps/share/src/auth.ts). */
private const val CALLBACK_PREFIX = "https://otter.letsmoe.com/auth/callback"

/** What the last sync did; failure is null when it worked. */
data class NeoworksSyncResult(val at: String, val received: Int, val sent: Int, val failure: String?)

data class NeoworksAccountState(
    val known: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val name: String? = null,
    val lastSync: NeoworksSyncResult? = null,
    /** A sign-in, sign-out or sync is running. */
    val busy: Boolean = false,
)

/**
 * The household's Neoworks account (sync). The backend holds the session; the app opens
 * the sign-in page and hands the callback's code back to it.
 */
object NeoworksAccountStore {

    private val mutableState = MutableStateFlow(NeoworksAccountState())
    val state: StateFlow<NeoworksAccountState> = mutableState

    fun refresh() {
        AppScope.launch {
            OtterApi.awaitBackend()
            runReporting("Could not check the Neoworks account") {
                val account = OtterApi.client.query(NeoworksAccountQuery()).execute().dataOrThrow().neoworksAccount
                show(account.neoworksAccountFields)
            }
        }
    }

    /** Opens Neoworks' sign-in page in the browser. */
    fun startSignIn(context: Context) {
        val applicationContext = context.applicationContext
        AppScope.launch {
            busy(true)
            runReporting("Could not start the sign-in") {
                val page = OtterApi.client.mutation(StartNeoworksSignInMutation()).execute().dataOrThrow().startNeoworksSignIn
                openWebPage(applicationContext, page)
            }
            busy(false)
        }
    }

    /** Takes a sign-in callback out of [intent]; false when it isn't one. */
    fun handleCallback(intent: Intent?): Boolean {
        val link = intent?.dataString ?: return false
        if (!link.startsWith(CALLBACK_PREFIX)) {
            return false
        }
        val callback = Uri.parse(link)
        val error = callback.getQueryParameter("error")
        val code = callback.getQueryParameter("code")
        val state = callback.getQueryParameter("state")
        if (error != null || code == null || state == null) {
            Toasts.push(signInRefusal(error), ToastKind.ERROR)
            return true
        }
        completeSignIn(code, state)
        return true
    }

    fun signOut() {
        AppScope.launch {
            busy(true)
            runReporting("Could not sign out") {
                val account = OtterApi.client.mutation(SignOutOfNeoworksMutation()).execute().dataOrThrow().signOutOfNeoworks
                show(account.neoworksAccountFields)
                Toasts.push("Signed out of Neoworks", ToastKind.SUCCESS)
            }
            busy(false)
        }
    }

    /**
     * Asks the backend to sync in the background, e.g. when the app comes to the front, so
     * changes from other devices show up. Quiet: a failed request just waits for the next sync.
     */
    fun syncSoon() {
        AppScope.launch {
            OtterApi.awaitBackend()
            try {
                OtterApi.client.mutation(SyncNeoworksSoonMutation()).execute()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // The backend syncs on its own every few minutes.
            }
        }
    }

    /** Syncs now; the result shows under Settings, Sync. */
    fun syncNow() {
        AppScope.launch {
            busy(true)
            runReporting("Sync failed") {
                val account = OtterApi.client.mutation(SyncNeoworksMutation()).execute().dataOrThrow().syncNeoworks
                show(account.neoworksAccountFields)
            }
            busy(false)
        }
    }

    private fun completeSignIn(code: String, state: String) {
        AppScope.launch {
            OtterApi.awaitBackend()
            busy(true)
            runReporting("Signing in to Neoworks failed") {
                val mutation = CompleteNeoworksSignInMutation(code = code, state = state)
                val account = OtterApi.client.mutation(mutation).execute().dataOrThrow().completeNeoworksSignIn
                show(account.neoworksAccountFields)
                Toasts.push("Signed in to Neoworks", ToastKind.SUCCESS)
            }
            busy(false)
        }
    }

    private fun show(account: NeoworksAccountFields) {
        val lastSync = account.lastSync?.let { sync -> NeoworksSyncResult(sync.at, sync.received, sync.sent, sync.failure) }
        mutableState.value = NeoworksAccountState(
            known = true,
            signedIn = account.signedIn,
            email = account.email,
            name = account.name,
            lastSync = lastSync,
        )
    }

    private fun busy(running: Boolean) {
        mutableState.update { current -> current.copy(busy = running) }
    }

    private suspend fun runReporting(failure: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Toasts.push(error.message ?: failure, ToastKind.ERROR)
        }
    }
}

private fun signInRefusal(error: String?): String {
    if (error == "access_denied") {
        return "Neoworks sign-in was cancelled"
    }
    return "Neoworks sign-in failed"
}
