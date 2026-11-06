package com.letsmoe.otter.ui.settings

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
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

val settingsLanguages = listOf(
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

/** App settings; every change saves on its own, there is no Save button. */
class SettingsViewModel : ViewModel() {

    var tmdbApiKey by mutableStateOf("")
    var tmdbLanguage by mutableStateOf("de-DE")
    var requireVpn by mutableStateOf(false)
    var hasBuiltInKey by mutableStateOf(false)
        private set
    var usesBuiltInKey by mutableStateOf(false)
        private set

    private var savedApiKey = ""

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val settings = OtterApi.client.query(AppSettingsQuery()).execute().dataOrThrow().settings
                tmdbApiKey = settings.tmdbApiKey
                savedApiKey = settings.tmdbApiKey
                tmdbLanguage = settings.tmdbLanguage
                requireVpn = settings.requireVpn
                hasBuiltInKey = settings.hasDefaultTmdbKey
                usesBuiltInKey = settings.usesDefaultTmdbKey
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Keeps the defaults; saving still works once the backend answers.
            }
        }
    }

    /** The language list, with an unlisted stored language kept selectable. */
    fun languageOptions(): List<Pair<String, String>> {
        if (settingsLanguages.any { language -> language.first == tmdbLanguage }) {
            return settingsLanguages
        }
        return listOf(tmdbLanguage to tmdbLanguage) + settingsLanguages
    }

    fun saveApiKey() {
        if (tmdbApiKey == savedApiKey) {
            return
        }
        savedApiKey = tmdbApiKey
        save(SettingsInput(tmdbApiKey = Optional.present(tmdbApiKey)))
    }

    /**
     * On clears the own key so the built-in one applies. Off only shows the key field: the built-in
     * key stays in use until an own key is saved.
     */
    fun changeUsesBuiltInKey(value: Boolean) {
        usesBuiltInKey = value
        if (!value) {
            return
        }
        tmdbApiKey = ""
        savedApiKey = ""
        save(SettingsInput(tmdbApiKey = Optional.present("")))
    }

    fun chooseLanguage(language: String) {
        tmdbLanguage = language
        save(SettingsInput(tmdbLanguage = Optional.present(language)))
    }

    fun changeRequireVpn(value: Boolean) {
        requireVpn = value
        save(SettingsInput(requireVpn = Optional.present(value)))
    }

    private fun save(input: SettingsInput) {
        viewModelScope.launch {
            try {
                OtterApi.client.mutation(UpdateAppSettingsMutation(input)).execute().dataOrThrow()
                Toasts.push("Saved", ToastKind.SUCCESS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not save", ToastKind.ERROR)
            }
        }
    }
}
