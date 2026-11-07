package com.letsmoe.otter.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import com.letsmoe.otter.downloads.DownloadItem
import com.letsmoe.otter.graphql.type.DownloadStatus
import com.letsmoe.otter.ui.components.squishOnChange
import com.letsmoe.otter.ui.theme.OtterColors

/** What the icon shows; changes between these animate, progress within one doesn't. */
private enum class DownloadVisual { NONE, PROGRESS, DONE, FAILED }

/**
 * Icon for a download's state: arrow (none), waiting ring, progress ring, phone with check (done)
 * or warning (failed). Starting a download drops the arrow into a growing ring; finishing pops the
 * check in. Mirrors DownloadState.svelte.
 */
@Composable
fun DownloadStateIcon(item: DownloadItem?, size: Dp = 26.dp) {
    val visual = visualOf(item)
    AnimatedContent(
        visual,
        modifier = Modifier.squishOnChange(visual),
        transitionSpec = { downloadTransition() },
        contentAlignment = Alignment.Center,
        label = "downloadState",
    ) { shown ->
        when (shown) {
            DownloadVisual.NONE -> Icon(PhosphorIcons.Regular.DownloadSimple, contentDescription = "Download", modifier = Modifier.size(size))
            DownloadVisual.PROGRESS -> ProgressRing(item, size)
            DownloadVisual.DONE -> DownloadedIcon(size)
            DownloadVisual.FAILED -> Icon(PhosphorIcons.Regular.WarningCircle, "Download failed", tint = OtterColors.error, modifier = Modifier.size(size))
        }
    }
}

private fun visualOf(item: DownloadItem?): DownloadVisual {
    if (item == null) {
        return DownloadVisual.NONE
    }
    return when (item.status) {
        DownloadStatus.DONE -> DownloadVisual.DONE
        DownloadStatus.FAILED -> DownloadVisual.FAILED
        else -> DownloadVisual.PROGRESS
    }
}

private val bouncyGrow = spring<Float>(dampingRatio = 0.45f, stiffness = 450f)

private fun AnimatedContentTransitionScope<DownloadVisual>.downloadTransition(): ContentTransform {
    val ringGrows = scaleIn(bouncyGrow, initialScale = 0.3f) + fadeIn(tween(150))
    if (initialState == DownloadVisual.NONE && targetState == DownloadVisual.PROGRESS) {
        // The arrow falls into the ring that grows where it was.
        val arrowDrops = slideOutVertically(tween(220)) { height -> height / 2 } + scaleOut(tween(220), targetScale = 0.4f) + fadeOut(tween(200))
        return ringGrows togetherWith arrowDrops using SizeTransform(clip = false)
    }
    return ringGrows togetherWith (scaleOut(tween(140), targetScale = 0.3f) + fadeOut(tween(140))) using SizeTransform(clip = false)
}

@Composable
private fun DownloadedIcon(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Regular.DeviceMobile, contentDescription = "Downloaded", modifier = Modifier.size(size))
        Icon(PhosphorIcons.Bold.Check, contentDescription = null, modifier = Modifier.size(size * 0.42f))
    }
}

// Also shown while leaving for another state, when the item may already be gone.
@Composable
private fun ProgressRing(item: DownloadItem?, size: Dp) {
    val color = LocalContentColor.current
    var waiting = false
    var progress = 0f
    if (item != null) {
        waiting = item.status == DownloadStatus.QUEUED
        progress = item.progress.toFloat().coerceIn(0f, 1f)
    }
    val shownProgress by animateFloatAsState(progress, tween(450), label = "downloadProgress")
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
        drawArc(color, -90f, 360f * shownProgress, false, Offset(inset, inset), arcSize, style = stroke)
    }
}
