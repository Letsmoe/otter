package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType

val CARD_SPACING = 14.dp
val REGULAR_POSTER_WIDTH = 104.dp
// Large posters still fit the home's row band (below its hero) when focused and grown.
val LARGE_POSTER_WIDTH = 128.dp
val WIDE_CARD_WIDTH = 200.dp
val REGULAR_LANDSCAPE_WIDTH = 168.dp

/** Where a focused card's top lands in a list of rows: just below its row's title. */
val ROW_FOCUS_LINE = 34.dp

/**
 * A row's title over its horizontally scrolling cards; the focused card stays [focusLine] from
 * the left, like a TV launcher's rows. [below] goes under the cards (a previewed title's info).
 */
@Composable
fun CardRow(
    title: String,
    focusLine: Dp = TvSpacing.screenHorizontal,
    below: @Composable () -> Unit = {},
    cards: LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            fontSize = TvType.rowTitle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = TvSpacing.screenHorizontal),
        )
        FocusPivot(focusLine) {
            LazyRow(
                // Room for the focused card to grow without being clipped.
                contentPadding = PaddingValues(horizontal = TvSpacing.screenHorizontal, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                content = cards,
            )
        }
        below()
    }
}
