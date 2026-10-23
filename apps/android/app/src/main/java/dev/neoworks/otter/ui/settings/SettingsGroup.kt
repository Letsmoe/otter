package dev.neoworks.otter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.components.frostedGlass

/**
 * A titled glass card of settings rows, like iOS / Netflix app settings. Separate rows with
 * [SettingsDivider]. Mirrors SettingsGroup.svelte.
 */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), content = content)
        if (footer != null) {
            Text(footer, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
}
