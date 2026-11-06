package com.letsmoe.otter.ui.home

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.components.ActorRail
import com.letsmoe.otter.ui.components.PosterRail
import com.letsmoe.otter.ui.components.PosterSize
import com.letsmoe.otter.ui.components.TopTenRail
import com.letsmoe.otter.ui.components.WideRail
import com.letsmoe.otter.ui.media.RailItem

/** Adds every row of the page to the lazy list, each in its own style. */
fun LazyListScope.homeRows(
    rows: List<HomeRow>,
    actors: List<ActorItem>,
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    for (row in rows) {
        item(key = row.key) {
            when (row) {
                is PosterRow -> PosterRowContent(row, onOpenItem)
                is ActorsRow -> ActorRail("Stars of What You've Watched", actors, onOpen = { actor -> onOpenPerson(actor.personId) })
            }
        }
    }
}

@Composable
private fun PosterRowContent(row: PosterRow, onOpenItem: (RailItem) -> Unit) {
    when (row.style) {
        RailStyle.REGULAR -> PosterRail(row.title, row.items, onOpenItem)
        RailStyle.LARGE -> PosterRail(row.title, row.items, onOpenItem, size = PosterSize.LARGE)
        RailStyle.WIDE -> WideRail(row.title, row.items, onOpenItem)
        RailStyle.TOP_TEN -> TopTenRail(row.title, row.items, onOpenItem)
    }
}
