package com.letsmoe.otter.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.ConnectLetterboxdMutation
import com.letsmoe.otter.graphql.DisconnectLetterboxdMutation
import com.letsmoe.otter.graphql.ImportLetterboxdMutation
import com.letsmoe.otter.graphql.LetterboxdFollowedQuery
import com.letsmoe.otter.graphql.LetterboxdImportStatusQuery
import com.letsmoe.otter.graphql.SyncLetterboxdMutation
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val POLL_MILLISECONDS = 1000L
private const val MAX_EXPORT_BYTES = 48 * 1024 * 1024

/** Settings, Letterboxd: importing an export ZIP and following a public diary feed. */
class LetterboxdViewModel : ViewModel() {

    var progress by mutableStateOf(ImportProgress())
        private set
    var account by mutableStateOf<FollowedAccount?>(null)
        private set
    /** What is typed in the username field. */
    var username by mutableStateOf("")
    var syncing by mutableStateOf(false)
        private set
    var reading by mutableStateOf(false)
        private set

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            loadAccount()
            loadProgress()
            followWhileRunning()
        }
    }

    private suspend fun loadAccount() {
        try {
            val data = OtterApi.client.query(LetterboxdFollowedQuery(profileId)).execute().dataOrThrow()
            account = data.letterboxdAccount?.letterboxdAccountFields?.toAccount()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // The group then offers to connect an account; nothing else to say.
        }
    }

    private suspend fun loadProgress() {
        try {
            val data = OtterApi.client.query(LetterboxdImportStatusQuery(profileId)).execute().dataOrThrow()
            progress = data.letterboxdImport.letterboxdImportFields.toProgress()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Keeps showing what it knew.
        }
    }

    // The backend matches films in the background; this reads its progress until it is done.
    private fun followWhileRunning() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (progress.phase == ImportPhase.RUNNING) {
                delay(POLL_MILLISECONDS)
                loadProgress()
            }
        }
    }

    /** Sends the picked export ZIP to the backend, which imports it into the active profile. */
    fun importFrom(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            reading = true
            try {
                val encoded = readEncoded(resolver, uri)
                startImport(encoded)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not import the file", ToastKind.ERROR)
            } finally {
                reading = false
            }
        }
    }

    private suspend fun readEncoded(resolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            val bytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
            if (bytes == null) {
                throw IllegalStateException("The file could not be opened")
            }
            if (bytes.size > MAX_EXPORT_BYTES) {
                throw IllegalStateException("This file is too big for a Letterboxd export")
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    private suspend fun startImport(encoded: String) {
        val mutation = ImportLetterboxdMutation(profileId, encoded)
        val data = OtterApi.client.mutation(mutation).execute().dataOrThrow()
        progress = data.importLetterboxd.letterboxdImportFields.toProgress()
        followWhileRunning()
    }

    /** Follows the typed username's public diary and reads it once. */
    fun connect() {
        viewModelScope.launch {
            syncing = true
            try {
                val mutation = ConnectLetterboxdMutation(profileId, username)
                OtterApi.client.mutation(mutation).execute().dataOrThrow()
                username = ""
                syncFeed()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not follow this account", ToastKind.ERROR)
            } finally {
                syncing = false
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncing = true
            try {
                syncFeed()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not read the diary", ToastKind.ERROR)
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun syncFeed() {
        val data = OtterApi.client.mutation(SyncLetterboxdMutation(profileId)).execute().dataOrThrow()
        account = data.syncLetterboxd.letterboxdAccountFields.toAccount()
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                OtterApi.client.mutation(DisconnectLetterboxdMutation(profileId)).execute().dataOrThrow()
                account = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not stop following", ToastKind.ERROR)
            }
        }
    }
}
