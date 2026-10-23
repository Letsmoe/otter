package dev.neoworks.otter.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.graphql.fragment.PluginFields
import dev.neoworks.otter.ui.components.GlassBottomSheet
import dev.neoworks.otter.ui.components.OtterSwitch
import dev.neoworks.otter.ui.components.PlainTextField
import dev.neoworks.otter.ui.theme.OtterColors

private val capabilityText = mapOf(
    "streams" to "Finds stream links for movies and episodes",
    "resolve" to "Turns hoster links into playable video",
)

/** A plugin's details in a bottom sheet: description, what it does, the on/off switch and its settings. Mirrors PluginSheet.svelte. */
@Composable
fun PluginSheet(plugin: PluginFields, viewModel: PluginsViewModel) {
    // Stored values first, then declared defaults.
    val draft = remember(plugin.name) {
        mutableStateMapOf<String, String>().apply {
            plugin.settings.forEach { field -> put(field.key, initialValue(field)) }
        }
    }
    GlassBottomSheet(onDismiss = { viewModel.openName = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SheetHeader(plugin, viewModel)
            if (!plugin.description.isNullOrEmpty()) {
                Text(plugin.description, fontSize = 14.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = 0.75f))
            }
            if (!plugin.available) {
                Text("Unavailable on this device.", fontSize = 14.sp, color = Color(0xFFFBBF24))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (capability in plugin.capabilities) {
                    CapabilityRow(capabilityLabel(capability))
                }
            }
            if (plugin.settings.isNotEmpty()) {
                SettingsFields(plugin, draft)
                SaveButton(viewModel.savingSettings) { viewModel.saveSettings(plugin, draft.toMap()) }
            }
        }
    }
}

private fun initialValue(field: PluginFields.Setting): String {
    return field.value ?: field.default ?: ""
}

private fun capabilityLabel(capability: String): String = capabilityText[capability] ?: capability

@Composable
private fun SheetHeader(plugin: PluginFields, viewModel: PluginsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PluginIcon(plugin.icon, large = true)
        Column(Modifier.weight(1f)) {
            Text(pluginName(plugin), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("v${plugin.version}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
        OtterSwitch(plugin.enabled) { if (viewModel.togglingName != plugin.name) viewModel.toggle(plugin) }
    }
}

@Composable
private fun CapabilityRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Color.White.copy(alpha = 0.5f), CircleShape))
        Text(text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun SettingsFields(plugin: PluginFields, draft: MutableMap<String, String>) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
        plugin.settings.forEachIndexed { index, field ->
            if (index > 0) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
            if (field.type == "boolean") {
                BooleanField(field, draft)
            } else {
                TextField(field, draft)
            }
        }
    }
}

@Composable
private fun BooleanField(field: PluginFields.Setting, draft: MutableMap<String, String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(field.label, fontSize = 14.sp)
        OtterSwitch(draft[field.key] == "true") { checked -> draft[field.key] = checked.toString() }
    }
}

@Composable
private fun TextField(field: PluginFields.Setting, draft: MutableMap<String, String>) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(field.label, fontSize = 14.sp)
            if (field.required) {
                Text(" *", fontSize = 14.sp, color = OtterColors.error)
            }
        }
        PlainTextField(
            value = draft[field.key].orEmpty(),
            onValueChange = { typed -> draft[field.key] = typed },
            placeholder = field.default.orEmpty(),
            isPassword = field.type == "password",
            textColor = Color.White.copy(alpha = 0.75f),
        )
        if (!field.description.isNullOrEmpty()) {
            Text(field.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SaveButton(saving: Boolean, onClick: () -> Unit) {
    var buttonAlpha = 1f
    if (saving) {
        buttonAlpha = 0.5f
    }
    Row(
        Modifier.fillMaxWidth().height(48.dp).alpha(buttonAlpha).background(Color.White, CircleShape).clickable(enabled = !saving, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saving) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
        }
        Text("Save", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
