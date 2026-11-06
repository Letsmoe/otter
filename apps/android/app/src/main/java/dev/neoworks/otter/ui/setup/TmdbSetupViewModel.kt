package dev.neoworks.otter.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.SaveTmdbSetupMutation
import dev.neoworks.otter.graphql.TmdbSetupQuery
import dev.neoworks.otter.graphql.fragment.TmdbSetupFields
import dev.neoworks.otter.graphql.type.SettingsInput
import dev.neoworks.otter.graphql.type.TmdbKeyStatus
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val REJECTED_KEY_MESSAGE = "TMDB didn't accept this key. Copy the “API Key”, not the Read Access Token."

/**
 * First-run TMDB setup: asks for an own API key until one works, or until the
 * user goes with the key built into this build.
 */
class TmdbSetupViewModel : ViewModel() {

    var visible by mutableStateOf(false)
        private set
    var apiKey by mutableStateOf("")
    var saving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Remembered from the first load: saving an own key hides that the build has one too. */
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
                builtInKeyAvailable = settings.usesDefaultTmdbKey && settings.tmdbKeyStatus == TmdbKeyStatus.OK
                visible = needsSetup(settings)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // An unreachable backend shows its own errors; the prompt returns on the next start.
            }
        }
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

// Shown on first start, and again whenever no working key is set.
private fun needsSetup(settings: TmdbSetupFields): Boolean {
    if (!settings.tmdbKeyPromptSeen) {
        return true
    }
    return settings.tmdbKeyStatus != TmdbKeyStatus.OK
}
