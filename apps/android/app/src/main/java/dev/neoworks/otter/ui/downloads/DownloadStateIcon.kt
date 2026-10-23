package dev.neoworks.otter.ui.downloads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.regular.DeviceMobile
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.WarningCircle
import dev.neoworks.otter.downloads.DownloadItem
import dev.neoworks.otter.graphql.type.DownloadStatus
import dev.neoworks.otter.ui.theme.OtterColors

/**
 * Icon for a download's state: arrow (none), waiting ring, progress ring, phone with check (done)
 * or warning (failed). Mirrors DownloadState.svelte.
 */
@Composable
fun DownloadStateIcon(item: DownloadItem?, size: Dp = 26.dp) {
    if (item == null) {
        Icon(PhosphorIcons.Regular.DownloadSimple, contentDescription = "Download", modifier = Modifier.size(size))
        return
    }
    when (item.status) {
        DownloadStatus.DONE -> DownloadedIcon(size)
        DownloadStatus.FAILED -> Icon(PhosphorIcons.Regular.WarningCircle, "Download failed", tint = OtterColors.error, modifier = Modifier.size(size))
        else -> ProgressRing(item, size)
    }
}

@Composable
private fun DownloadedIcon(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Regular.DeviceMobile, contentDescription = "Downloaded", modifier = Modifier.size(size))
        Icon(PhosphorIcons.Bold.Check, contentDescription = null, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
private fun ProgressRing(item: DownloadItem, size: Dp) {
    val color = LocalContentColor.current
    val waiting = item.status == DownloadStatus.QUEUED
    val spin by rememberInfiniteTransition(label = "downloadSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "downloadSpinAngle",
    )
    Canvas(Modifier.size(size)) {
        val strokeWidth = this.size.minDimension * (2f / 24f)
        val inset = strokeWidth / 2
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth))
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
        if (waiting) {
            drawArc(color, spin - 90f, 100f, false, Offset(inset, inset), arcSize, style = stroke)
            return@Canvas
        }
        val sweep = 360f * item.progress.toFloat().coerceIn(0f, 1f)
        drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = stroke)
    }
}
