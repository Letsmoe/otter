package dev.neoworks.otter.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.neoworks.otter.ui.theme.OtterColors

/** Neoworks switch: white track with a black thumb when on. */
@Composable
fun OtterSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Color.White,
            checkedThumbColor = Color.Black,
            uncheckedTrackColor = OtterColors.base300,
            uncheckedThumbColor = OtterColors.baseContent.copy(alpha = 0.4f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}
