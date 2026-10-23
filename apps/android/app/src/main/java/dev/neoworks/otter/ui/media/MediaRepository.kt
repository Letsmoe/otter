package dev.neoworks.otter.ui.media

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.BrowseQuery
import dev.neoworks.otter.graphql.WatchedActorsQuery
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.graphql.CatalogQuery
import dev.neoworks.otter.graphql.CatalogRowsQuery
import dev.neoworks.otter.graphql.ContinueWatchingQuery
import dev.neoworks.otter.graphql.FeaturedTrendingQuery
import dev.neoworks.otter.graphql.GenresQuery
import dev.neoworks.otter.graphql.HistoryQuery
import dev.neoworks.otter.graphql.MoreWithQuery
import dev.neoworks.otter.graphql.NewAndComingSoonQuery
import dev.neoworks.otter.graphql.PersonFilmographyQuery
import dev.neoworks.otter.graphql.RecommendationsQuery
import dev.neoworks.otter.graphql.RelatedTitlesQuery
import dev.neoworks.otter.graphql.SearchQuery
import dev.neoworks.otter.graphql.TrendingQuery
import dev.neoworks.otter.graphql.WatchlistQuery
import dev.neoworks.otter.graphql.CatalogThemeQuery
import dev.neoworks.otter.graphql.RecommendationRowsQuery
import dev.neoworks.otter.graphql.type.CatalogSort
import dev.neoworks.otter.graphql.type.CatalogTheme
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.graphql.type.TitleFilter
import kotlin.coroutines.cancellation.CancellationException

const val LIBRARY_PAGE_SIZE = 40
private const val GENRE_RAIL_SIZE = 20
private const val BECAUSE_ROW_COUNT = 8
private const val BECAUSE_ROW_SIZE = 30
private const val WATCH_AGAIN_HISTORY_SIZE = 100

/** A title to feature at the top of a home page. */
data class FeaturedItem(val item: RailItem, val genres: List<String>)

data class MoreWithRail(val personName: String, val items: List<RailItem>)

data class GenreRail(val genre: String, val items: List<RailItem>)

/**
 * Titles for rails, grids and search. Like the web app's discovery helpers, a failed
 * request yields an empty result so one broken row never blanks a page.
 */
object MediaRepository {

    private suspend fun <D : Operation.Data> run(call: suspend () -> ApolloResponse<D>): D = call().dataOrThrow()

    private suspend fun <T> orDefault(default: T, block: suspend () -> T): T {
        try {
            return block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return default
        }
    }

    suspend fun trending(mediaType: MediaType?): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(TrendingQuery(Optional.presentIfNotNull(mediaType))).execute() }
        data.trending.map { title -> title.titleCard.toRailItem() }
    }

    suspend fun continueWatching(profileId: String, limit: Int = 20): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(ContinueWatchingQuery(profileId, limit)).execute() }
        data.continueWatching.map { entry ->
            var item = entry.title.titleCard.toRailItem()
            if (entry.progress != null) {
                val progress = entry.progress.progressFields
                item = item.copy(progress = progressFraction(progress.progressSeconds, progress.durationSeconds))
            }
            if (entry.episode != null) {
                item = item.withEpisode(entry.episode.episodeRef)
            }
            item
        }
    }

    suspend fun history(profileId: String, limit: Int = 50): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(HistoryQuery(profileId, limit)).execute() }
        data.history.map { entry ->
            val progress = entry.progress.progressFields
            var item = entry.title.titleCard.toRailItem().copy(
                progress = progressFraction(progress.progressSeconds, progress.durationSeconds),
                watchedAt = progress.updatedAt,
            )
            if (entry.episode != null) {
                item = item.withEpisode(entry.episode.episodeRef)
            }
            item
        }
    }

    /** Finished titles of one kind, most recently watched first, without repeats. */
    suspend fun watchAgain(profileId: String, mediaType: MediaType): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(HistoryQuery(profileId, WATCH_AGAIN_HISTORY_SIZE)).execute() }
        val finished = data.history.filter { entry -> entry.progress.progressFields.completed }
        val items = finished.map { entry -> entry.title.titleCard.toRailItem() }
        items.distinctBy { item -> item.id }.onlyKind(mediaType)
    }

    suspend fun watchlist(profileId: String): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(WatchlistQuery(profileId)).execute() }
        data.watchlist.map { title -> title.titleCard.toRailItem() }
    }

    suspend fun recommended(profileId: String, mediaType: MediaType, limit: Int = 24): List<RailItem> =
        orDefault(emptyList()) {
            val data = run { OtterApi.client.query(RecommendationsQuery(profileId, mediaType, limit)).execute() }
            data.recommendations.map { entry -> entry.title.titleCard.toRailItem() }
        }

    /** The profile's best recommendation of that kind, else the top trending one. */
    suspend fun featured(profileId: String, mediaType: MediaType): FeaturedItem? = orDefault(null) {
        val recommendations = run { OtterApi.client.query(RecommendationsQuery(profileId, mediaType, 10)).execute() }
        val recommended = recommendations.recommendations.map { entry -> entry.title }.firstOrNull { title -> title.titleCard.posterPath != null }
        if (recommended != null) {
            return@orDefault FeaturedItem(recommended.titleCard.toRailItem(), recommended.genres)
        }
        val trending = run { OtterApi.client.query(FeaturedTrendingQuery(mediaType)).execute() }
        val top = trending.trending.firstOrNull { title -> title.titleCard.posterPath != null }
        if (top == null) {
            return@orDefault null
        }
        FeaturedItem(top.titleCard.toRailItem(), top.genres)
    }

    /** Upcoming releases (with a date badge), then recent ones. */
    suspend fun newAndComingSoon(mediaType: MediaType): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(NewAndComingSoonQuery(mediaType)).execute() }
        data.newAndComingSoon.map { title -> title.titleCard.toRailItem().copy(badge = releaseBadge(title.releaseDate)) }
    }

    suspend fun moreWith(profileId: String, mediaType: MediaType): MoreWithRail? = orDefault(null) {
        val data = run { OtterApi.client.query(MoreWithQuery(profileId, mediaType)).execute() }
        val row = data.moreWith
        if (row == null) {
            return@orDefault null
        }
        MoreWithRail(row.person.name, row.items.map { title -> title.titleCard.toRailItem() })
    }

    /** One rail per genre the profile's recommendations lean towards most. */
    suspend fun genreRails(profileId: String, mediaType: MediaType, count: Int = 3): List<GenreRail> =
        orDefault(emptyList()) {
            val data = run { OtterApi.client.query(RecommendationsQuery(profileId, mediaType, 40)).execute() }
            val genres = topGenres(data.recommendations.flatMap { entry -> entry.title.genres }, count)
            genres.map { genre -> GenreRail(genre, browse(mediaType, genre, GENRE_RAIL_SIZE, 0)) }
        }

    /** Popular or top-rated titles from TMDB's catalog, for rows the profile can't fill yet. */
    suspend fun catalog(mediaType: MediaType, sort: CatalogSort): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(CatalogQuery(mediaType, sort)).execute() }
        data.catalog.map { title -> title.titleCard.toRailItem() }
    }

    /** Genre rails of popular TMDB titles, skipping the genres in [exclude]. */
    suspend fun catalogGenreRails(mediaType: MediaType, count: Int, exclude: List<String>): List<GenreRail> =
        orDefault(emptyList()) {
            val data = run { OtterApi.client.query(CatalogRowsQuery(mediaType, count, exclude)).execute() }
            data.catalogRows.map { row -> GenreRail(row.genre, row.items.map { title -> title.titleCard.toRailItem() }) }
        }

    /** One themed TMDB row (hidden gems, blockbusters, ...); empty when the theme doesn't fit the kind. */
    suspend fun theme(mediaType: MediaType, theme: CatalogTheme): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(CatalogThemeQuery(mediaType, theme)).execute() }
        data.catalogTheme.map { title -> title.titleCard.toRailItem() }
    }

    /** "Because you watched X" rows, kept to titles of [mediaType]. */
    suspend fun becauseRows(profileId: String, mediaType: MediaType): List<GenreRail> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(RecommendationRowsQuery(profileId, BECAUSE_ROW_COUNT, BECAUSE_ROW_SIZE)).execute() }
        data.recommendationRows.map { row ->
            GenreRail(row.title, row.items.map { title -> title.titleCard.toRailItem() }.onlyKind(mediaType))
        }
    }

    /** Lead actors of the profile's recently watched titles of one kind; empty before any history. */
    suspend fun watchedActors(profileId: String, mediaType: MediaType, limit: Int = 15): List<ActorItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(WatchedActorsQuery(profileId, mediaType, limit)).execute() }
        data.watchedActors.map { person -> ActorItem(person.id, person.name, tmdbImageUrl(person.profilePath, "w185")) }
    }

    private fun topGenres(genres: List<String>, count: Int): List<String> =
        genres.groupingBy { genre -> genre }.eachCount().entries.sortedByDescending { entry -> entry.value }.take(count).map { entry -> entry.key }

    /** One page of stored titles of one kind, most popular first. */
    suspend fun browse(mediaType: MediaType, genre: String?, limit: Int = LIBRARY_PAGE_SIZE, offset: Int = 0): List<RailItem> =
        orDefault(emptyList()) {
            val query = BrowseQuery(mediaType, Optional.presentIfNotNull(genre), limit, offset)
            run { OtterApi.client.query(query).execute() }.browse.map { title -> title.titleCard.toRailItem() }
        }

    /** Genre names for the browse filter, alphabetical. */
    suspend fun genres(): List<String> = orDefault(emptyList()) {
        run { OtterApi.client.query(GenresQuery()).execute() }.genres.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    suspend fun search(query: String, mediaType: MediaType?, filter: TitleFilter): List<RailItem> = orDefault(emptyList()) {
        val request = SearchQuery(query, Optional.presentIfNotNull(mediaType), 40, Optional.present(filter))
        run { OtterApi.client.query(request).execute() }.search.map { title -> title.titleCard.toRailItem() }
    }

    suspend fun related(titleId: String, limit: Int = 20): List<RailItem> = orDefault(emptyList()) {
        val data = run { OtterApi.client.query(RelatedTitlesQuery(titleId, limit)).execute() }
        val title = data.title
        if (title == null) {
            return@orDefault emptyList()
        }
        title.related.map { related -> related.titleCard.toRailItem() }
    }

    /** A person's credited titles of one kind, newest first; `job` keeps only those crew credits. */
    suspend fun filmography(personId: String, mediaType: MediaType, job: String? = null): List<RailItem> =
        orDefault(emptyList()) {
            val data = run { OtterApi.client.query(PersonFilmographyQuery(personId, Optional.presentIfNotNull(job))).execute() }
            val person = data.person
            if (person == null) {
                return@orDefault emptyList()
            }
            person.filmography.map { title -> title.titleCard.toRailItem() }.onlyKind(mediaType)
        }
}
