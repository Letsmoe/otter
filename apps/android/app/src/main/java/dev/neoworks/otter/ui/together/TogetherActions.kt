package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.SignOut
import dev.neoworks.otter.ui.components.SubtleFill
import dev.neoworks.otter.ui.theme.OtterColors

enum class ActionStyle { PRIMARY, SOFT }

/** Pill button: PRIMARY is solid white, SOFT a quiet translucent fill. */
@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var fill = SubtleFill
    var contentColor = Color.White
    var borderColor = Color.White.copy(alpha = 0.1f)
    if (style == ActionStyle.PRIMARY) {
        fill = Color.White
        contentColor = Color.Black
        borderColor = Color.Transparent
    }
    var contentAlpha = 1f
    if (!enabled) {
        contentAlpha = 0.4f
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(contentAlpha)
            .pressable(enabled = enabled, onClick = onClick)
            .background(fill, CircleShape)
            .border(1.dp, borderColor, CircleShape),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Text(label, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Quiet destructive text button. */
@Composable
fun LeaveRoomButton(onLeave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).pressable(onClick = onLeave),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.SignOut, contentDescription = null, tint = OtterColors.error, modifier = Modifier.size(18.dp))
        Text("Leave room", color = OtterColors.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
