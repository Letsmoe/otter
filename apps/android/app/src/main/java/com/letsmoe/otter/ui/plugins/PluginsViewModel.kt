package com.letsmoe.otter.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.PluginsQuery
import com.letsmoe.otter.graphql.SetPluginEnabledMutation
import com.letsmoe.otter.graphql.SetPluginSettingsMutation
import com.letsmoe.otter.graphql.fragment.PluginFields
import com.letsmoe.otter.graphql.type.PluginSettingInput
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.LoadState
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
