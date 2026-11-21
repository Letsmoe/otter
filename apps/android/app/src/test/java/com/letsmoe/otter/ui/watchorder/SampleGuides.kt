package com.letsmoe.otter.ui.watchorder

import android.graphics.Bitmap

/** A franchise for previews: real names and years, with generated artwork in place of TMDB's. */
class SampleFranchise(
    val id: String,
    val name: String,
    val hue: Float,
    val films: List<Pair<String, Int>>,
)

object SampleGuides {

    val harryPotter = SampleFranchise(
        "collection:1241", "Harry Potter Collection", 262f,
        listOf(
            "Harry Potter and the Philosopher's Stone" to 2001,
            "Harry Potter and the Chamber of Secrets" to 2002,
            "Harry Potter and the Prisoner of Azkaban" to 2004,
            "Harry Potter and the Goblet of Fire" to 2005,
            "Harry Potter and the Order of the Phoenix" to 2007,
            "Harry Potter and the Half-Blood Prince" to 2009,
            "Harry Potter and the Deathly Hallows: Part 1" to 2010,
            "Harry Potter and the Deathly Hallows: Part 2" to 2011,
        ),
    )

    val lordOfTheRings = SampleFranchise(
        "collection:119", "The Lord of the Rings Collection", 28f,
        listOf(
            "The Fellowship of the Ring" to 2001,
            "The Two Towers" to 2002,
            "The Return of the King" to 2003,
        ),
    )

    val matrix = SampleFranchise(
        "collection:2344", "The Matrix Collection", 140f,
        listOf(
            "The Matrix" to 1999,
            "The Matrix Reloaded" to 2003,
            "The Matrix Revolutions" to 2003,
            "The Matrix Resurrections" to 2021,
        ),
    )

    val jamesBond = SampleFranchise(
        "collection:645", "James Bond Collection", 210f,
        listOf(
            "Dr. No" to 1962, "From Russia with Love" to 1963, "Goldfinger" to 1964, "Thunderball" to 1965,
            "You Only Live Twice" to 1967, "On Her Majesty's Secret Service" to 1969, "Diamonds Are Forever" to 1971,
            "Live and Let Die" to 1973, "The Man with the Golden Gun" to 1974, "The Spy Who Loved Me" to 1977,
            "Moonraker" to 1979, "For Your Eyes Only" to 1981, "Octopussy" to 1983, "A View to a Kill" to 1985,
            "The Living Daylights" to 1987, "Licence to Kill" to 1989, "GoldenEye" to 1995, "Tomorrow Never Dies" to 1997,
            "The World Is Not Enough" to 1999, "Die Another Day" to 2002, "Casino Royale" to 2006,
            "Quantum of Solace" to 2008, "Skyfall" to 2012, "Spectre" to 2015, "No Time to Die" to 2021,
        ),
    )

    /** The guide of [franchise] after the first [watched] films, with [inProgress] in progress. */
    fun guideOf(franchise: SampleFranchise, watched: Int, progressOfNext: Float = 0f): WatchOrderGuide {
        val parts = franchise.films.mapIndexed { index, (title, year) -> part(index, title, year, watched, progressOfNext) }
        return WatchOrderGuide(
            id = franchise.id,
            name = franchise.name,
            overview = null,
            backdropUrl = "backdrop",
            posterUrl = null,
            parts = parts,
            nextNumber = parts.firstOrNull { part -> part.status != PartStatus.WATCHED }?.number,
        )
    }

    private fun part(index: Int, title: String, year: Int, watched: Int, progressOfNext: Float): GuidePart {
        var status = PartStatus.NOT_YET
        var progress = 0f
        if (index < watched) {
            status = PartStatus.WATCHED
            progress = 1f
        }
        if (index == watched && progressOfNext > 0f) {
            status = PartStatus.IN_PROGRESS
            progress = progressOfNext
        }
        return GuidePart(index + 1, "movie:${index + 1}", title, year, "poster-$index", status, progress, released = true)
    }

    fun images(franchise: SampleFranchise): ArtworkImages {
        val posters = mutableMapOf<Int, Bitmap>()
        franchise.films.forEachIndexed { index, (title, _) ->
            posters[index] = SampleArt.poster(title, (franchise.hue + index * 11f) % 360f)
        }
        return ArtworkImages(SampleArt.backdrop(franchise.hue), posters)
    }
}
