package com.letsmoe.otter.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.SaveTmdbSetupMutation
import com.letsmoe.otter.graphql.TmdbSetupQuery
import com.letsmoe.otter.graphql.fragment.TmdbSetupFields
import com.letsmoe.otter.graphql.type.SettingsInput
import com.letsmoe.otter.graphql.type.TmdbKeyStatus
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val REJECTED_KEY_MESSAGE = "TMDB didn't accept this key. Copy the “API Key”, not the Read Access Token."

enum class SetupStep { CHOOSE_KEY, OWN_KEY }

/**
 * First-run TMDB setup: the user picks the key built into this build or their own, and is asked
 * again whenever no working key is set.
 */
class TmdbSetupViewModel : ViewModel() {

    var visible by mutableStateOf(false)
        private set
    var step by mutableStateOf(SetupStep.OWN_KEY)
        private set
    var apiKey by mutableStateOf("")
    var saving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Whether this build comes with a TMDB key of its own. */
    var builtInKeyAvailable by mutableStateOf(false)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val settings = OtterApi.client.query(TmdbSetupQuery()).execute().dataOrThrow().settings.tmdbSetupFields
                builtInKeyAvailable = settings.hasDefaultTmdbKey
                if (builtInKeyAvailable) {
                    step = SetupStep.CHOOSE_KEY
                }
                visible = needsSetup(settings)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // An unreachable backend shows its own errors; the prompt returns on the next start.
            }
        }
    }

    fun chooseOwnKey() {
        step = SetupStep.OWN_KEY
    }

    fun backToChoice() {
        errorMessage = null
        step = SetupStep.CHOOSE_KEY
    }

    fun saveKey() {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return
        }
        submit(SettingsInput(tmdbApiKey = Optional.present(trimmedKey), tmdbKeyPromptSeen = Optional.present(true)))
    }

    fun useBuiltInKey() {
        submit(SettingsInput(tmdbApiKey = Optional.present(""), tmdbKeyPromptSeen = Optional.present(true)))
    }

    private fun submit(input: SettingsInput) {
        viewModelScope.launch {
            saving = true
            errorMessage = null
            try {
                val settings = OtterApi.client.mutation(SaveTmdbSetupMutation(input)).execute().dataOrThrow().updateSettings.tmdbSetupFields
                showResult(settings)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not save the key"
            } finally {
                saving = false
            }
        }
    }

    private fun showResult(settings: TmdbSetupFields) {
        if (settings.tmdbKeyStatus == TmdbKeyStatus.OK) {
            visible = false
            return
        }
        errorMessage = REJECTED_KEY_MESSAGE
    }
}

// Shown whenever no working key is set, and on first start while only the built-in key is in use.
// An own key that works (e.g. saved before this prompt existed) never triggers it.
private fun needsSetup(settings: TmdbSetupFields): Boolean {
    if (settings.tmdbKeyStatus != TmdbKeyStatus.OK) {
        return true
    }
    return settings.usesDefaultTmdbKey && !settings.tmdbKeyPromptSeen
}
