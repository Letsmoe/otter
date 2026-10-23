package dev.neoworks.otter.ui.home

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.ui.components.ActorRail
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.components.PosterSize
import dev.neoworks.otter.ui.components.TopTenRail
import dev.neoworks.otter.ui.components.WideRail
import dev.neoworks.otter.ui.media.RailItem

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
