package dev.neoworks.otter.ui.title

import dev.neoworks.otter.api.backdropUrl
import dev.neoworks.otter.api.posterUrl
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.TitleDetailQuery
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.toRailItem

data class CastMember(val personId: String, val name: String, val character: String?, val order: Int, val profileUrl: String?)

data class DirectorPerson(val personId: String, val name: String)

data class SeasonInfo(val seasonNumber: Int, val name: String?, val episodeCount: Int)

data class TitleDetail(
    val id: String,
    val mediaType: MediaType,
    val title: String,
    val overview: String?,
    val year: Int?,
    /** Minutes; movies only. */
    val runtime: Int?,
    val voteAverage: Double,
    /** The backdrop, or the poster when the title has none. */
    val bannerUrl: String?,
    val trailerKey: String?,
    val genres: List<String>,
    val cast: List<CastMember>,
    val directors: List<DirectorPerson>,
    val seasons: List<SeasonInfo>,
    val related: List<RailItem>,
    val inWatchlist: Boolean,
    val rating: Double?,
) {
    /** Seasons without specials. */
    val regularSeasonCount: Int get() = seasons.count { season -> season.seasonNumber > 0 }
}

private const val DIRECTOR_JOB = "Director"
private const val CAST_ROLE = "cast"
private const val CREW_ROLE = "crew"

fun TitleDetailQuery.Title.toDetail(): TitleDetail {
    var banner = backdropUrl(backdropPath)
    if (banner == null) {
        banner = posterUrl(posterPath)
    }
    return TitleDetail(
        id = id,
        mediaType = mediaType,
        title = title,
        overview = overview,
        year = year,
        runtime = runtime,
        voteAverage = voteAverage,
        bannerUrl = banner,
        trailerKey = trailerKey,
        genres = genres,
        cast = castOf(this),
        directors = directorsOf(this),
        seasons = seasons.sortedBy { season -> season.seasonNumber }.map { season -> SeasonInfo(season.seasonNumber, season.name, season.episodeCount) },
        related = related.map { title -> title.titleCard.toRailItem() },
        inWatchlist = inWatchlist,
        rating = rating,
    )
}

private fun castOf(title: TitleDetailQuery.Title): List<CastMember> =
    title.credits
        .filter { credit -> credit.role == CAST_ROLE }
        .sortedBy { credit -> credit.order }
        .map { credit ->
            CastMember(credit.person.id, credit.person.name, credit.character, credit.order, tmdbImageUrl(credit.person.profilePath, "w185"))
        }

private fun directorsOf(title: TitleDetailQuery.Title): List<DirectorPerson> =
    title.credits
        .filter { credit -> credit.role == CREW_ROLE && credit.job == DIRECTOR_JOB }
        .map { credit -> DirectorPerson(credit.person.id, credit.person.name) }
