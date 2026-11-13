package com.letsmoe.otter.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.LoginPluginMutation
import com.letsmoe.otter.graphql.LogoutPluginMutation
import com.letsmoe.otter.graphql.PluginsQuery
import com.letsmoe.otter.graphql.SetPluginEnabledMutation
import com.letsmoe.otter.graphql.SetPluginSettingsMutation
import com.letsmoe.otter.graphql.UpdatePluginsMutation
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

    var checkingUpdates by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                mutableState.value = LoadState.Loaded(fetchPlugins())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = LoadState.Failed(error.message.orEmpty())
            }
        }
    }

    /** Asks the backend to install newer plugin releases now, then shows the new versions. */
    fun checkForUpdates() {
        if (checkingUpdates) {
            return
        }
        checkingUpdates = true
        viewModelScope.launch {
            try {
                val report = OtterApi.client.mutation(UpdatePluginsMutation()).execute().dataOrThrow().updatePlugins
                mutableState.value = LoadState.Loaded(fetchPlugins())
                announceUpdates(report)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not check for updates", ToastKind.ERROR)
            } finally {
                checkingUpdates = false
            }
        }
    }

    private suspend fun fetchPlugins(): List<PluginFields> {
        val data = OtterApi.client.query(PluginsQuery()).execute().dataOrThrow()
        return data.plugins.map { plugin -> plugin.pluginFields }
    }

    private fun announceUpdates(report: UpdatePluginsMutation.UpdatePlugins) {
        if (report.failed.isNotEmpty()) {
            Toasts.push("Could not update ${report.failed.joinToString()}", ToastKind.ERROR)
        }
        if (report.updated.isEmpty()) {
            if (report.failed.isEmpty()) {
                Toasts.push("All plugins are up to date", ToastKind.SUCCESS)
            }
            return
        }
        val names = report.updated.joinToString { update -> "${update.name} ${update.toVersion}" }
        Toasts.push("Updated $names", ToastKind.SUCCESS)
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

    var loggingIn by mutableStateOf(false)
        private set

    /** Logs in to the plugin's site; the backend keeps the account only when the site accepts it. */
    fun login(plugin: PluginFields, username: String, password: String) {
        loggingIn = true
        viewModelScope.launch {
            try {
                val mutation = LoginPluginMutation(plugin.name, username, password)
                replace(OtterApi.client.mutation(mutation).execute().dataOrThrow().loginPlugin.pluginFields)
                Toasts.push("Logged in", ToastKind.SUCCESS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not log in", ToastKind.ERROR)
            } finally {
                loggingIn = false
            }
        }
    }

    fun logout(plugin: PluginFields) {
        viewModelScope.launch {
            try {
                replace(OtterApi.client.mutation(LogoutPluginMutation(plugin.name)).execute().dataOrThrow().logoutPlugin.pluginFields)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not log out", ToastKind.ERROR)
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
