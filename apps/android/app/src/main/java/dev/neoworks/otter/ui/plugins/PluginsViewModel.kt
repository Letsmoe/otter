package dev.neoworks.otter.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.PluginsQuery
import dev.neoworks.otter.graphql.SetPluginEnabledMutation
import dev.neoworks.otter.graphql.SetPluginSettingsMutation
import dev.neoworks.otter.graphql.fragment.PluginFields
import dev.neoworks.otter.graphql.type.PluginSettingInput
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The installed plugins: switching them on and off, and their settings. */
class PluginsViewModel : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<List<PluginFields>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<PluginFields>>> = mutableState

    /** Name of the plugin whose details are open. */
    var openName by mutableStateOf<String?>(null)

    /** Name of the plugin being switched. */
    var togglingName by mutableStateOf<String?>(null)
        private set

    var savingSettings by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val data = OtterApi.client.query(PluginsQuery()).execute().dataOrThrow()
                mutableState.value = LoadState.Loaded(data.plugins.map { plugin -> plugin.pluginFields })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = LoadState.Failed(error.message.orEmpty())
            }
        }
    }

    fun toggle(plugin: PluginFields) {
        togglingName = plugin.name
        viewModelScope.launch {
            try {
                val updated = OtterApi.client.mutation(SetPluginEnabledMutation(plugin.name, !plugin.enabled)).execute().dataOrThrow()
                replace(updated.setPluginEnabled.pluginFields)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message.orEmpty(), ToastKind.ERROR)
            } finally {
                togglingName = null
            }
        }
    }

    /** Saves the plugin's settings; closes its details on success. */
    fun saveSettings(plugin: PluginFields, values: Map<String, String>) {
        savingSettings = true
        viewModelScope.launch {
            try {
                val inputs = values.map { (key, value) -> PluginSettingInput(key, value) }
                val updated = OtterApi.client.mutation(SetPluginSettingsMutation(plugin.name, inputs)).execute().dataOrThrow()
                replace(updated.setPluginSettings.pluginFields)
                Toasts.push("Settings saved", ToastKind.SUCCESS)
                openName = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not save", ToastKind.ERROR)
            } finally {
                savingSettings = false
            }
        }
    }

    private fun replace(updated: PluginFields) {
        mutableState.update { current ->
            if (current is LoadState.Loaded) {
                LoadState.Loaded(current.value.map { plugin -> replacement(plugin, updated) })
            } else {
                current
            }
        }
    }

    private fun replacement(plugin: PluginFields, updated: PluginFields): PluginFields {
        if (plugin.name == updated.name) {
            return updated
        }
        return plugin
    }
}
