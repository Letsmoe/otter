package com.letsmoe.otter.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

const val TMDB_API_KEY_PAGE = "https://www.themoviedb.org/settings/api"

/** Opens the page in the browser. */
fun openWebPage(context: Context, url: String) {
    val page = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
