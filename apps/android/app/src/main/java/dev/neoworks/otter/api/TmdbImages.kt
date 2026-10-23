package dev.neoworks.otter.api

// Mirrors packages/otter-app/src/tmdb.ts.
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"

/**
 * TMDB images are stored as CDN-relative paths ("/abc.jpg"); posters from
 * plugins are stored as absolute URLs and pass through untouched.
 */
fun tmdbImageUrl(path: String?, size: String): String? {
    if (path.isNullOrEmpty()) {
        return null
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
        return path
    }
    return "$TMDB_IMAGE_BASE/$size$path"
}

fun posterUrl(path: String?): String? = tmdbImageUrl(path, "w342")

fun backdropUrl(path: String?): String? = tmdbImageUrl(path, "w1280")
