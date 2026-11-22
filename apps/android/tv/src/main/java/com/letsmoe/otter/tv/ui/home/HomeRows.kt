package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.ui.components.ActorCard
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.PageRow
import com.letsmoe.otter.tv.ui.components.PosterPreviewCard
import com.letsmoe.otter.tv.ui.components.RANK_NUMBER_WIDTH
import com.letsmoe.otter.tv.ui.components.REGULAR_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.TOP_TEN_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.TopTenCard
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.home.ActorsRow
import com.letsmoe.otter.ui.home.HomeRow
import com.letsmoe.otter.ui.home.PosterRow
import com.letsmoe.otter.ui.home.RailStyle
import com.letsmoe.otter.ui.media.RailItem

/** What a row does when a title or person is selected. */
class RowActions(
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

/** Every home row as a page row, each in its own card style. */
fun homePageRows(rows: List<HomeRow>, actors: List<ActorItem>, actions: RowActions): List<PageRow> {
    return rows.map { row ->
        PageRow(row.key) {
            when (row) {
                is PosterRow -> PosterRowContent(row, actions)
                is ActorsRow -> ActorRow("Stars of What You've Watched", actors, actions.onOpenPerson)
            }
        }
    }
}

@Composable
private fun PosterRowContent(row: PosterRow, actions: RowActions) {
    var focusLine = TvSpacing.screenHorizontal
    if (row.style == RailStyle.TOP_TEN) {
        // The focused poster lands right of its rank number, which stays on screen.
        focusLine = TvSpacing.screenHorizontal + RANK_NUMBER_WIDTH
    }
    CardRow(row.title, focusLine) {
        itemsIndexed(row.items, key = { _, item -> item.id + item.episodeId.orEmpty() }) { index, item ->
            TitleCardFor(row.style, item, position = index + 1, actions)
        }
    }
}

/**
 * [position] is the card's 1-based place in its row; the Top 10 row shows it as the rank. Every
 * row is posters that grow into a trailer preview with details when focus rests on them.
 */
@Composable
private fun TitleCardFor(style: RailStyle, item: RailItem, position: Int, actions: RowActions) {
    if (style == RailStyle.TOP_TEN) {
        TopTenCard(position, posterHeight = TOP_TEN_POSTER_WIDTH * 3f / 2f) {
            PosterPreviewCard(item, TOP_TEN_POSTER_WIDTH, onClick = { actions.onOpenItem(item) })
        }
        return
    }
    PosterPreviewCard(item, REGULAR_POSTER_WIDTH, onClick = { actions.onOpenItem(item) })
}

/** A row of round actor portraits. */
@Composable
fun ActorRow(title: String, actors: List<ActorItem>, onOpenPerson: (String) -> Unit) {
    CardRow(title) {
        items(actors, key = { actor -> actor.personId }) { actor ->
            ActorCard(actor, onClick = { onOpenPerson(actor.personId) })
        }
    }
}
