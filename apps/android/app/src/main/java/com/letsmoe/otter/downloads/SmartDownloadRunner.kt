package com.letsmoe.otter.downloads

import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.DeleteDownloadMutation
import com.letsmoe.otter.graphql.DownloadsQuery
import com.letsmoe.otter.graphql.EpisodeLinksQuery
import com.letsmoe.otter.graphql.MovieLinksQuery
import com.letsmoe.otter.graphql.SmartDownloadSourcesQuery
import com.letsmoe.otter.graphql.StartDownloadMutation
import com.letsmoe.otter.graphql.type.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// A backend on this phone only downloads while the app's process lives, so the work waits for it.
private const val FINISH_WAIT_MILLISECONDS = 8 * 60_000L
private const val FINISH_POLL_MILLISECONDS = 10_000L

/** What Smart Downloads finds to do for one profile. */
private class SmartSources(val candidates: List<SmartCandidate>, val watchedMediaIds: Set<String>)

/**
 * Keeps the profile's smart downloads within the budget: deletes the watched ones, starts the next
 * episodes of series in progress and a few recommended films. Manual downloads are never touched:
 * only downloads this started ([SmartDownloadStore]) are deleted, and they alone count for the budget.
 */
object SmartDownloadRunner {

    // The charging and the idle schedule may fire together.
    private val running = Mutex()

    suspend fun run(profileId: String) {
        running.withLock {
            val quality = SmartDownloadStore.quality
            val sources = loadSources(profileId, quality)
            val downloads = loadDownloads(profileId)
            forgetVanished(downloads)
            val plan = planSmartDownloads(
                budgetBytes = gigabytesToBytes(SmartDownloadStore.budgetGigabytes),
                existing = smartDownloadsOf(downloads, quality),
                watchedMediaIds = sources.watchedMediaIds,
                candidates = sources.candidates,
                skipped = downloads.map { item -> item.mediaId }.toSet() + SmartDownloadStore.removedMedia(),
            )
            deleteWatched(plan.delete)
            startNew(profileId, plan.start, quality)
            waitForSmartDownloads(profileId)
        }
    }

    private suspend fun loadDownloads(profileId: String): List<DownloadItem> {
        val data = OtterApi.client.query(DownloadsQuery(profileId)).execute().dataOrThrow()
        return data.downloads.map { download -> download.downloadFields.toDownloadItem() }
    }

    // A smart download the viewer deleted elsewhere (another device on the NAS) is no longer ours.
    private fun forgetVanished(downloads: List<DownloadItem>) {
        val present = downloads.map { item -> item.id }.toSet()
        for (smartId in SmartDownloadStore.smartIds.value - present) {
            SmartDownloadStore.forget(smartId)
        }
    }

    private fun smartDownloadsOf(downloads: List<DownloadItem>, quality: SmartQuality): List<SmartDownload> {
        return downloads.filter { item -> SmartDownloadStore.isSmart(item.id) }.map { item ->
            var bytes = item.sizeBytes.toLong()
            if (bytes <= 0) {
                bytes = estimatedBytes(UNSIZED_MINUTES, quality)
            }
            SmartDownload(item.id, item.mediaId, bytes)
        }
    }

    private suspend fun deleteWatched(watched: List<SmartDownload>) {
        for (download in watched) {
            OtterApi.client.mutation(DeleteDownloadMutation(download.downloadId)).execute().dataOrThrow()
            SmartDownloadStore.forget(download.downloadId)
        }
    }

    private suspend fun startNew(profileId: String, candidates: List<SmartCandidate>, quality: SmartQuality) {
        for (candidate in candidates) {
            try {
                val linkId = chooseLink(candidate.mediaId, quality)
                val started = OtterApi.client.mutation(StartDownloadMutation(profileId, candidate.mediaId, Optional.presentIfNotNull(linkId)))
                    .execute().dataOrThrow()
                SmartDownloadStore.markSmart(started.startDownload.downloadFields.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // This one can't be fetched now (no links yet, VPN rule): the next run tries again.
                continue
            }
        }
    }

    // The best stored link within the chosen quality; none makes the backend pick its own.
    private suspend fun chooseLink(mediaId: String, quality: SmartQuality): String? {
        val links = storedLinks(mediaId)
        return pickLinkFor(links, quality.height)?.id
    }

    private suspend fun storedLinks(mediaId: String): List<DownloadLink> {
        if (mediaId.startsWith("episode:")) {
            val episode = OtterApi.client.query(EpisodeLinksQuery(mediaId)).execute().dataOrThrow().episode
            return episode?.streams.orEmpty().map { link -> DownloadLink(link.id, link.quality, link.playable) }
        }
        val title = OtterApi.client.query(MovieLinksQuery(mediaId)).execute().dataOrThrow().title
        return title?.streams.orEmpty().map { link -> DownloadLink(link.id, link.quality, link.playable) }
    }

    private suspend fun loadSources(profileId: String, quality: SmartQuality): SmartSources {
        val data = OtterApi.client.query(SmartDownloadSourcesQuery(profileId)).execute().dataOrThrow()
        val continuing = data.continueWatching.map { entry ->
            ContinueSource(
                isSeries = entry.title.mediaType == MediaType.SERIES,
                mediaId = entry.title.id,
                runtimeMinutes = entry.title.runtime,
                episodeId = entry.episode?.id,
                episodeMinutes = entry.episode?.runtime,
                nextEpisodeId = entry.episode?.next?.id,
                nextEpisodeMinutes = entry.episode?.next?.runtime,
            )
        }
        val recommended = data.recommendations.map { entry -> RecommendedMovie(entry.title.id, entry.title.runtime) }
        val watched = data.history.filter { entry -> entry.progress.completed }.map { entry -> entry.progress.mediaId }.toSet()
        return SmartSources(smartCandidates(continuing, recommended, quality), watched)
    }

    // Returns once the smart downloads finished, or after a while: the next run continues.
    private suspend fun waitForSmartDownloads(profileId: String) {
        withTimeoutOrNull(FINISH_WAIT_MILLISECONDS) {
            while (hasUnfinishedSmartDownload(profileId)) {
                delay(FINISH_POLL_MILLISECONDS)
            }
        }
    }

    private suspend fun hasUnfinishedSmartDownload(profileId: String): Boolean {
        return loadDownloads(profileId).any { item -> SmartDownloadStore.isSmart(item.id) && !item.isFinished }
    }

    // A download whose size isn't known yet counts as an hour.
    private const val UNSIZED_MINUTES = 60
}
