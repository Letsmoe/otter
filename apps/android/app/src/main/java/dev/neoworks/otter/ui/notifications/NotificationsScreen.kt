package dev.neoworks.otter.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.notifications.NotificationItem
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.profile.NotificationRow

/** Every notification of the profile, in one glass card. Mirrors routes/notifications/+page.svelte. */
@Composable
fun NotificationsScreen(
    onOpenNotification: (NotificationItem) -> Unit,
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Notifications", onBack)
        when (val current = state) {
            is LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
            is LoadState.Failed -> Unit
            is LoadState.Loaded -> NotificationList(current.value, onOpenNotification)
        }
    }
}

@Composable
private fun NotificationList(notifications: List<NotificationItem>, onOpen: (NotificationItem) -> Unit) {
    if (notifications.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
            Text("No notifications yet.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance())) {
        item {
            Column(
                Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (notification in notifications) {
                    NotificationRow(notification, onOpen = { onOpen(notification) })
                }
            }
        }
    }
}
