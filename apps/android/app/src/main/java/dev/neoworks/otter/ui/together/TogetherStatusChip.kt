package dev.neoworks.otter.ui.together

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.graphql.type.TogetherStatus
import dev.neoworks.otter.ui.theme.OtterColors

@Composable
internal fun StatusChip(status: TogetherStatus) {
    Row(
        Modifier.height(30.dp).background(Color.White.copy(alpha = 0.08f), CircleShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(status)
        Text(statusText(status), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun StatusDot(status: TogetherStatus) {
    val pulse by rememberInfiniteTransition(label = "statusPulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "statusPulseAlpha",
    )
    var dotAlpha = 1f
    if (isSearching(status)) {
        dotAlpha = pulse
    }
    Box(Modifier.size(6.dp).alpha(dotAlpha).background(statusColor(status), CircleShape))
}

private fun statusText(status: TogetherStatus): String {
    return when (status) {
        TogetherStatus.WAITING -> "Waiting for friends"
        TogetherStatus.CONNECTING -> "Connecting"
        TogetherStatus.CLOSED -> "Room ended"
        TogetherStatus.FAILED -> "Host unreachable"
        else -> "Connected"
    }
}

private fun statusColor(status: TogetherStatus): Color {
    return when (status) {
        TogetherStatus.CONNECTED -> OtterColors.success
        TogetherStatus.CLOSED, TogetherStatus.FAILED -> OtterColors.error
        else -> OtterColors.warning
    }
}

internal fun isSearching(status: TogetherStatus): Boolean = status == TogetherStatus.WAITING || status == TogetherStatus.CONNECTING
