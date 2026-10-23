package dev.neoworks.otter.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Warning
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.Plugs
import dev.neoworks.otter.graphql.fragment.PluginFields
import dev.neoworks.otter.ui.FailedMessage
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.components.OtterSwitch
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.theme.OtterColors

/** Plugins as a glass list: icon, name, what it does and an on/off switch; a row opens its details. Mirrors routes/plugins/+page.svelte. */
@Composable
fun PluginsScreen(onBack: () -> Unit, viewModel: PluginsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Plugins", onBack)
        when (val current = state) {
            is LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
            is LoadState.Failed -> FailedMessage(current.message, onRetry = viewModel::load)
            is LoadState.Loaded -> PluginList(current.value, viewModel)
        }
    }
}

@Composable
private fun PluginList(plugins: List<PluginFields>, viewModel: PluginsViewModel) {
    val openPlugin = plugins.firstOrNull { plugin -> plugin.name == viewModel.openName }
    if (openPlugin != null) {
        PluginSheet(openPlugin, viewModel)
    }
    if (plugins.isEmpty()) {
        EmptyPlugins()
        return
    }
    val enabledCount = plugins.count { plugin -> plugin.enabled }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance())) {
        item {
            Text("$enabledCount of ${plugins.size} enabled", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
                plugins.forEachIndexed { index, plugin ->
                    if (index > 0) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                    PluginRow(plugin, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: PluginFields, viewModel: PluginsViewModel) {
    var rowAlpha = 1f
    if (!plugin.enabled) {
        rowAlpha = 0.55f
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).alpha(rowAlpha).clickable { viewModel.openName = plugin.name },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIcon(plugin.icon)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pluginName(plugin), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (needsSetup(plugin)) {
                        Icon(PhosphorIcons.Fill.Warning, contentDescription = "Needs setup", tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                    }
                }
                Text("${plugin.capabilities.joinToString(" · ")} · v${plugin.version}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.size(16.dp).alpha(0.4f))
        }
        OtterSwitch(plugin.enabled) { if (viewModel.togglingName != plugin.name) viewModel.toggle(plugin) }
    }
}

fun pluginName(plugin: PluginFields): String {
    if (plugin.displayName.isNullOrEmpty()) {
        return plugin.name
    }
    return plugin.displayName
}

private fun needsSetup(plugin: PluginFields): Boolean {
    return plugin.settings.any { field -> field.required && field.value.isNullOrBlank() }
}

@Composable
private fun EmptyPlugins() {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(PhosphorIcons.Regular.Plugs, contentDescription = null, modifier = Modifier.size(56.dp).alpha(0.4f))
        Text("No plugins loaded", fontWeight = FontWeight.SemiBold)
        Text("Plugin bundles in the plugins folder show up here.", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(280.dp).alpha(0.6f))
    }
}
