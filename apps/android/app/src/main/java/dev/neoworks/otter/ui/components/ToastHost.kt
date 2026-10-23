package dev.neoworks.otter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Info
import com.adamglin.phosphoricons.fill.WarningCircle
import com.adamglin.phosphoricons.regular.X
import dev.neoworks.otter.toasts.Toast
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.theme.OtterColors

private val infoColor = Color(0xFF60A5FA)

/** Glass toasts dropping in under the status bar; they dismiss themselves via [Toasts]. Mirrors NotificationManager.svelte. */
@Composable
fun ToastHost() {
    val entries by Toasts.entries.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (toast in entries) {
            ToastPill(toast)
        }
    }
}

@Composable
private fun ToastPill(toast: Toast) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState, enter = slideInVertically { height -> -height } + fadeIn()) {
        Row(
            modifier = Modifier
                .widthIn(max = 384.dp)
                .frostedGlass(CircleShape)
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(toastIcon(toast.kind), contentDescription = null, tint = toastColor(toast.kind), modifier = Modifier.size(18.dp))
            Text(
                toast.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                PhosphorIcons.Regular.X,
                contentDescription = "Dismiss",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp).clickable { Toasts.dismiss(toast.id) }.padding(5.dp),
            )
        }
    }
}

private fun toastIcon(kind: ToastKind): ImageVector {
    return when (kind) {
        ToastKind.SUCCESS -> PhosphorIcons.Fill.CheckCircle
        ToastKind.ERROR -> PhosphorIcons.Fill.WarningCircle
        ToastKind.INFO -> PhosphorIcons.Fill.Info
    }
}

private fun toastColor(kind: ToastKind): Color {
    return when (kind) {
        ToastKind.SUCCESS -> OtterColors.success
        ToastKind.ERROR -> OtterColors.error
        ToastKind.INFO -> infoColor
    }
}
