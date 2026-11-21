package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.ui.components.ActorCard
import com.letsmoe.otter.tv.ui.components.FocusPivot
import com.letsmoe.otter.tv.ui.components.PosterCard
import com.letsmoe.otter.tv.ui.components.TopTenCard
import com.letsmoe.otter.tv.ui.components.WideCard
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.home.ActorsRow
import com.letsmoe.otter.ui.home.HomeRow
import com.letsmoe.otter.ui.home.PosterRow
import com.letsmoe.otter.ui.home.RailStyle
import com.letsmoe.otter.ui.media.RailItem

private val REGULAR_POSTER_WIDTH = 150.dp
private val LARGE_POSTER_WIDTH = 200.dp
private val WIDE_CARD_WIDTH = 300.dp
private val CARD_SPACING = 20.dp

/** What a row does when the D-pad lands on or selects a title. */
class RowActions(
    val onFocusItem: (RailItem) -> Unit,
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

/** Adds every home row to the list, each in its own card style. */
fun LazyListScope.homeRows(rows: List<HomeRow>, actors: List<ActorItem>, actions: RowActions) {
    for (row in rows) {
        item(key = row.key) {
            when (row) {
                is PosterRow -> PosterRowContent(row, actions)
                is ActorsRow -> ActorRow("Stars of What You've Watched", actors, actions)
            }
        }
    }
}

@Composable
private fun PosterRowContent(row: PosterRow, actions: RowActions) {
    RowFrame(row.title) {
        items(row.items, key = { item -> item.id + item.episodeId.orEmpty() }) { item ->
            TitleCardFor(row.style, item, actions)
        }
    }
}

@Composable
private fun TitleCardFor(style: RailStyle, item: RailItem, actions: RowActions) {
    val onFocus = { actions.onFocusItem(item) }
    val onClick = { actions.onOpenItem(item) }
    when (style) {
        RailStyle.REGULAR -> PosterCard(item, REGULAR_POSTER_WIDTH, onFocus, onClick)
        RailStyle.LARGE -> PosterCard(item, LARGE_POSTER_WIDTH, onFocus, onClick)
        RailStyle.WIDE -> WideCard(item, WIDE_CARD_WIDTH, onFocus, onClick)
        RailStyle.TOP_TEN -> TopTenCard(item, onFocus, onClick)
    }
}

@Composable
private fun ActorRow(title: String, actors: List<ActorItem>, actions: RowActions) {
    RowFrame(title) {
        items(actors, key = { actor -> actor.personId }) { actor ->
            ActorCard(actor, onClick = { actions.onOpenPerson(actor.personId) })
        }
    }
}

/** A row's title over its horizontally scrolling cards; focus stays at the row's left keyline. */
@Composable
private fun RowFrame(title: String, cards: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = TvSpacing.screenHorizontal),
        )
        FocusPivot(TvSpacing.screenHorizontal) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvSpacing.screenHorizontal, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                content = cards,
            )
        }
    }
}
