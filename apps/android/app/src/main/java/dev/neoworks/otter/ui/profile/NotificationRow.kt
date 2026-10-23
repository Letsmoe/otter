package dev.neoworks.otter.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.neoworks.otter.notifications.NotificationItem
import dev.neoworks.otter.notifications.notificationAge
import dev.neoworks.otter.ui.theme.OtterColors

/** One notification: still or backdrop, a red dot while unread, heading, body and age. Mirrors NotificationRow.svelte. */
@Composable
fun NotificationRow(notification: NotificationItem, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!notification.read) {
            Box(Modifier.size(6.dp).background(OtterColors.error, CircleShape))
        }
        NotificationImage(notification)
        Column(Modifier.weight(1f)) {
            Text(notification.heading, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bodyText(notification), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(notificationAge(notification.createdAt), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

private fun bodyText(notification: NotificationItem): String {
    if (notification.body.isNullOrEmpty()) {
        return notification.titleName
    }
    return notification.body
}

@Composable
private fun NotificationImage(notification: NotificationItem) {
    Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))) {
        if (notification.imageUrl != null) {
            AsyncImage(notification.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            return@Box
        }
        Text(
            notification.titleName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(8.dp),
        )
    }
}
