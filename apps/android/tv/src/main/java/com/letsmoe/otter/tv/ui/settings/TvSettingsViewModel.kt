package com.letsmoe.otter.tv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.AppSettingsQuery
import com.letsmoe.otter.graphql.UpdateAppSettingsMutation
import com.letsmoe.otter.graphql.type.SettingsInput
import com.letsmoe.otter.reporting.ErrorReporting
import com.letsmoe.otter.settings.AutoSkipIntro
import com.letsmoe.otter.settings.PlaybackSettings
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The languages TMDB titles can come in, as (TMDB code, name). */
val titleLanguages = listOf(
    "en-US" to "English",
    "de-DE" to "Deutsch",
    "fr-FR" to "Français",
    "es-ES" to "Español",
    "it-IT" to "Italiano",
    "nl-NL" to "Nederlands",
    "pl-PL" to "Polski",
    "pt-BR" to "Português (Brasil)",
    "tr-TR" to "Türkçe",
    "ja-JP" to "日本語",
)

/**
 * The settings the TV uses: the backend's (title language, VPN requirement, error reports) and
 * this device's playback ones. Every change saves on its own; a backend change that fails is undone.
 */
class TvSettingsViewModel : ViewModel() {

    var titleLanguage by mutableStateOf("")
        private set
    var requireVpn by mutableStateOf(false)
        private set
    var errorReporting by mutableStateOf(ErrorReporting.isEnabled())
        private set
    var skipIntros by mutableStateOf(PlaybackSettings.autoSkipIntro == AutoSkipIntro.ALWAYS)
        private set
    var evenVolume by mutableStateOf(PlaybackSettings.evenVolume)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val settings = OtterApi.client.query(AppSettingsQuery()).execute().dataOrThrow().settings
                titleLanguage = settings.tmdbLanguage
                requireVpn = settings.requireVpn
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Keeps the defaults; changes still save once the backend answers.
            }
        }
    }

    /** The language's name; the code itself for one not in [titleLanguages]. */
    fun titleLanguageName(): String {
        val known = titleLanguages.firstOrNull { language -> language.first == titleLanguage }
        if (known == null) {
            return titleLanguage
        }
        return known.second
    }

    /** Moves to the next language in [titleLanguages], wrapping around. */
    fun nextTitleLanguage() {
        val position = titleLanguages.indexOfFirst { language -> language.first == titleLanguage }
        val next = titleLanguages[(position + 1) % titleLanguages.size].first
        val previous = titleLanguage
        titleLanguage = next
        save(SettingsInput(tmdbLanguage = Optional.present(next)), undo = { titleLanguage = previous })
    }

    fun toggleRequireVpn() {
        val wanted = !requireVpn
        requireVpn = wanted
        save(SettingsInput(requireVpn = Optional.present(wanted)), undo = { requireVpn = !wanted })
    }

    /** Turns reporting off for the app and for the backend it talks to; both go back when the backend refuses. */
    fun toggleErrorReporting() {
        val wanted = !errorReporting
        applyErrorReporting(wanted)
        save(SettingsInput(errorReporting = Optional.present(wanted)), undo = { applyErrorReporting(!wanted) })
    }

    private fun applyErrorReporting(enabled: Boolean) {
        errorReporting = enabled
        ErrorReporting.setEnabled(enabled)
    }

    // The TV player plays on this device, so only "Always" skips here; off is "Off".
    fun toggleSkipIntros() {
        skipIntros = !skipIntros
        if (skipIntros) {
            PlaybackSettings.autoSkipIntro = AutoSkipIntro.ALWAYS
        } else {
            PlaybackSettings.autoSkipIntro = AutoSkipIntro.OFF
        }
    }

    fun toggleEvenVolume() {
        evenVolume = !evenVolume
        PlaybackSettings.evenVolume = evenVolume
    }

    private fun save(input: SettingsInput, undo: () -> Unit) {
        viewModelScope.launch {
            try {
                OtterApi.client.mutation(UpdateAppSettingsMutation(input)).execute().dataOrThrow()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                undo()
            }
        }
    }
}
