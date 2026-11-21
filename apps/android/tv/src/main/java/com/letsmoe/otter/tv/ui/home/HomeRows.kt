package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.ui.components.ActorCard
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.LARGE_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.PageRow
import com.letsmoe.otter.tv.ui.components.PosterCard
import com.letsmoe.otter.tv.ui.components.RANK_NUMBER_WIDTH
import com.letsmoe.otter.tv.ui.components.REGULAR_LANDSCAPE_WIDTH
import com.letsmoe.otter.tv.ui.components.ROW_SPACING
import com.letsmoe.otter.tv.ui.components.TOP_TEN_POSTER_WIDTH
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.tv.ui.components.TopTenCard
import com.letsmoe.otter.tv.ui.components.WIDE_CARD_WIDTH
import com.letsmoe.otter.tv.ui.components.WideCard
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.home.ActorsRow
import com.letsmoe.otter.ui.home.HomeRow
import com.letsmoe.otter.ui.home.PosterRow
import com.letsmoe.otter.ui.home.RailStyle
import com.letsmoe.otter.ui.media.RailItem

/** What a row does when the D-pad lands on or selects a title. */
class RowActions(
    val onFocusItem: (RailItem) -> Unit,
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

// A row's title and the padding that leaves room for the focused card to grow.
private val ROW_CHROME_HEIGHT = 42.dp
// How much of the second row shows under the first one on the first screen.
private val SECOND_ROW_PEEK = 46.dp

/** Room under the hero for the first row and the top of the second, like Prime Video's home. */
fun firstScreenRowSpace(firstRow: HomeRow?): Dp {
    return ROW_CHROME_HEIGHT + cardHeightOf(firstRow) + ROW_SPACING + SECOND_ROW_PEEK
}

private fun cardHeightOf(row: HomeRow?): Dp {
    if (row !is PosterRow) {
        // The actor row, or no rows yet.
        return 120.dp
    }
    return when (row.style) {
        RailStyle.REGULAR -> REGULAR_LANDSCAPE_WIDTH * 9f / 16f
        RailStyle.WIDE -> WIDE_CARD_WIDTH * 9f / 16f
        RailStyle.LARGE -> LARGE_POSTER_WIDTH * 3f / 2f
        RailStyle.TOP_TEN -> TOP_TEN_POSTER_WIDTH * 3f / 2f
    }
}

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

/** [position] is the card's 1-based place in its row; the Top 10 row shows it as the rank. */
@Composable
private fun TitleCardFor(style: RailStyle, item: RailItem, position: Int, actions: RowActions) {
    val onFocus = { actions.onFocusItem(item) }
    val onClick = { actions.onOpenItem(item) }
    when (style) {
        // Landscape like Prime Video's rows; large and Top 10 rows stay posters for variety.
        RailStyle.REGULAR -> WideCard(item, REGULAR_LANDSCAPE_WIDTH, onFocus, onClick)
        RailStyle.LARGE -> PosterCard(item, LARGE_POSTER_WIDTH, onFocus, onClick)
        RailStyle.WIDE -> WideCard(item, WIDE_CARD_WIDTH, onFocus, onClick)
        RailStyle.TOP_TEN -> TopTenCard(item, position, onFocus, onClick)
    }
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
