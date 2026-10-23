package dev.neoworks.otter.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.notifications.NotificationItem
import dev.neoworks.otter.notifications.NotificationsRepository
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val NOTIFICATION_LIMIT = 100

/** Every notification of the profile; opening the page marks them read. */
class NotificationsViewModel : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<List<NotificationItem>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<NotificationItem>>> = mutableState

    init {
        load()
    }

    private fun load() {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val notifications = NotificationsRepository.fetch(profileId, NOTIFICATION_LIMIT)
            mutableState.value = LoadState.Loaded(notifications)
            if (notifications.any { notification -> !notification.read }) {
                NotificationsRepository.markAllRead(profileId)
            }
        }
    }
}
