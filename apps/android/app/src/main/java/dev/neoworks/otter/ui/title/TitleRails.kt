package dev.neoworks.otter.ui.title

import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.media.MediaRepository
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.withoutRepeats
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class TitleRail(val key: String, val title: String, val items: List<RailItem>)

/**
 * Grouped "more like this" rows for a title page: related titles, the lead actor's and
 * director's other work, and the first genre. Each title shows once, and never the page's own.
 */
suspend fun loadTitleRails(detail: TitleDetail): List<TitleRail> = coroutineScope {
    val pending = mutableListOf(async { TitleRail("related", "More Like This", detail.related) })
    val lead = detail.cast.firstOrNull()
    if (lead != null) {
        pending.add(async { TitleRail("lead", "More with ${lead.name}", MediaRepository.filmography(lead.personId, detail.mediaType)) })
    }
    val director = detail.directors.firstOrNull()
    if (director != null) {
        pending.add(async { TitleRail("director", "Directed by ${director.name}", MediaRepository.filmography(director.personId, detail.mediaType, "Director")) })
    }
    val genre = detail.genres.firstOrNull()
    if (genre != null) {
        pending.add(async { TitleRail("genre", "$genre ${pluralOf(detail.mediaType)}", MediaRepository.browse(detail.mediaType, genre, 20, 0)) })
    }
    val loaded = pending.map { rail -> rail.await() }
    val unique = withoutRepeats(loaded.map { rail -> rail.items }, listOf(detail.id))
    loaded.mapIndexed { index, rail -> rail.copy(items = unique[index]) }
}

private fun pluralOf(mediaType: MediaType): String {
    if (mediaType == MediaType.SERIES) {
        return "Shows"
    }
    return "Movies"
}
