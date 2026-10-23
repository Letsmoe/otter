package dev.neoworks.otter.downloads

import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.AppScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.DeleteDownloadMutation
import dev.neoworks.otter.graphql.DownloadsQuery
import dev.neoworks.otter.graphql.StartDownloadMutation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The active profile's downloads, shared by the Downloads tab, the profile
 * card and the download buttons. The backend downloads; this polls while
 * one is still running. Mirrors stores/downloads.svelte.ts.
 */
object DownloadsRepository {

    private const val POLL_INTERVAL_MILLISECONDS = 2000L

    private val mutableItems = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = mutableItems

    private val mutableLoaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = mutableLoaded

    private var profileId: String? = null
    private var pollJob: Job? = null

    /** Switches to a profile's downloads (null when nobody is signed in). */
    fun follow(newProfileId: String?) {
        if (newProfileId == profileId) {
            return
        }
        profileId = newProfileId
        mutableItems.value = emptyList()
        mutableLoaded.value = false
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        val followedProfileId = profileId ?: return
        pollJob = AppScope.launch { pollWhileRunning(followedProfileId) }
    }

    private suspend fun pollWhileRunning(followedProfileId: String) {
        while (true) {
            val loadedItems = fetchDownloads(followedProfileId)
            if (followedProfileId != profileId) {
                return
            }
            if (loadedItems != null) {
                mutableItems.value = loadedItems
                mutableLoaded.value = true
            }
            val stillRunning = loadedItems != null && loadedItems.any { item -> !item.isFinished }
            if (!stillRunning) {
                return
            }
            delay(POLL_INTERVAL_MILLISECONDS)
        }
    }

    private suspend fun fetchDownloads(followedProfileId: String): List<DownloadItem>? {
        return try {
            val data = OtterApi.client.query(DownloadsQuery(followedProfileId)).execute().dataOrThrow()
            data.downloads.map { download -> download.downloadFields.toDownloadItem() }
        } catch (error: Exception) {
            null
        }
    }

    fun forMedia(mediaId: String): DownloadItem? = mutableItems.value.firstOrNull { item -> item.mediaId == mediaId }

    fun findById(downloadId: String): DownloadItem? = mutableItems.value.firstOrNull { item -> item.id == downloadId }

    /** Queues a download; without [streamId] the backend tries the best links. Throws with the server's message. */
    suspend fun start(mediaId: String, streamId: String?) {
        val activeProfileId = profileId ?: return
        val mutation = StartDownloadMutation(activeProfileId, mediaId, Optional.presentIfNotNull(streamId))
        OtterApi.client.mutation(mutation).execute().dataOrThrow()
        refresh()
    }

    suspend fun remove(downloadId: String) {
        mutableItems.update { current -> current.filter { item -> item.id != downloadId } }
        OtterApi.client.mutation(DeleteDownloadMutation(downloadId)).execute().dataOrThrow()
        refresh()
    }
}
