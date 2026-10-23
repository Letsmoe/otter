package dev.neoworks.otter.downloads

import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.fragment.DownloadFields
import dev.neoworks.otter.graphql.type.DownloadStatus

data class DownloadEpisode(val seasonNumber: Int, val episodeNumber: Int, val name: String?)

/** A movie or episode saved for offline playback by the backend. */
data class DownloadItem(
    val id: String,
    val status: DownloadStatus,
    val progress: Double,
    val sizeBytes: Double,
    val error: String?,
    /** movie:… or episode:…, the id the player tracks progress under. */
    val mediaId: String,
    val titleId: String,
    val titleName: String,
    val imageUrl: String?,
    val posterUrl: String?,
    val episode: DownloadEpisode?,
    val url: String?,
    val mimeType: String?,
) {
    val isFinished: Boolean
        get() = status == DownloadStatus.DONE || status == DownloadStatus.FAILED

    val isPlayable: Boolean
        get() = status == DownloadStatus.DONE && url != null
}

/** A movie, or all downloaded episodes of one series. */
data class DownloadGroup(
    val titleId: String,
    val titleName: String,
    val imageUrl: String?,
    val posterUrl: String?,
    val items: List<DownloadItem>,
) {
    val isSeries: Boolean
        get() = titleId.startsWith("series:")
}

private const val STILL_SIZE = "w780"
private const val POSTER_SIZE = "w342"

fun DownloadFields.toDownloadItem(): DownloadItem {
    val titleImage = tmdbImageUrl(title.backdropPath, STILL_SIZE)
    val episodeFields = episode
    var mediaId = title.id
    var imageUrl = titleImage
    var downloadEpisode: DownloadEpisode? = null
    if (episodeFields != null) {
        mediaId = episodeFields.id
        imageUrl = tmdbImageUrl(episodeFields.stillPath, STILL_SIZE) ?: titleImage
        downloadEpisode = DownloadEpisode(episodeFields.seasonNumber, episodeFields.episodeNumber, episodeFields.name)
    }
    return DownloadItem(
        id = id,
        status = status,
        progress = progress,
        sizeBytes = sizeBytes,
        error = error,
        mediaId = mediaId,
        titleId = title.id,
        titleName = title.title,
        imageUrl = imageUrl,
        posterUrl = tmdbImageUrl(title.posterPath, POSTER_SIZE),
        episode = downloadEpisode,
        url = url,
        mimeType = mimeType,
    )
}

/** Groups episodes under their series; movies stay alone. Keeps newest-first order. */
fun groupDownloads(items: List<DownloadItem>): List<DownloadGroup> {
    return items.groupBy { item -> item.titleId }.map { (titleId, groupItems) ->
        val first = groupItems.first()
        DownloadGroup(titleId, first.titleName, first.imageUrl, first.posterUrl, groupItems)
    }
}

/** "344.9 MB", "1.2 GB"; empty when the size is unknown. */
fun formatSize(bytes: Double): String {
    if (bytes <= 0) {
        return ""
    }
    val megabytes = bytes / (1024 * 1024)
    if (megabytes < 1024) {
        return String.format(java.util.Locale.US, "%.1f MB", megabytes)
    }
    return String.format(java.util.Locale.US, "%.1f GB", megabytes / 1024)
}
