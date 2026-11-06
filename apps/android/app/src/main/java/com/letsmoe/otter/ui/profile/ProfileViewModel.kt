package com.letsmoe.otter.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.ProfileLikedQuery
import com.letsmoe.otter.notifications.NotificationItem
import com.letsmoe.otter.notifications.NotificationsRepository
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val NOTIFICATION_PREVIEW_COUNT = 3
private const val HISTORY_LIMIT = 20

data class ProfilePageData(
    val notifications: List<NotificationItem> = emptyList(),
    val notificationsLoaded: Boolean = false,
    val watchlist: List<RailItem> = emptyList(),
    val history: List<RailItem> = emptyList(),
    val liked: List<RailItem> = emptyList(),
)

/** The profile page's notifications preview and My List, Watch History and liked rails. */
class ProfileViewModel : ViewModel() {

    private val mutableData = MutableStateFlow(ProfilePageData())
    val data: StateFlow<ProfilePageData> = mutableData

    /** Loads everything for [profileId]; shows what is there already while it refreshes. */
    fun load(profileId: String) {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val notifications = async { NotificationsRepository.fetch(profileId, NOTIFICATION_PREVIEW_COUNT) }
            val watchlist = async { MediaRepository.watchlist(profileId) }
            val history = async { MediaRepository.history(profileId, HISTORY_LIMIT) }
            val liked = async { fetchLiked(profileId) }
            mutableData.value = ProfilePageData(
                notifications = notifications.await(),
                notificationsLoaded = true,
                watchlist = watchlist.await(),
                history = history.await(),
                liked = liked.await(),
            )
        }
    }

    private suspend fun fetchLiked(profileId: String): List<RailItem> {
        return try {
            val data = OtterApi.client.query(ProfileLikedQuery(profileId)).execute().dataOrThrow()
            data.likedTitles.map { title -> title.titleCard.toRailItem() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emptyList()
        }
    }
}
