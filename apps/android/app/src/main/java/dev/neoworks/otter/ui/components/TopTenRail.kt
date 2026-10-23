package dev.neoworks.otter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.media.RailItem

private val posterWidth = 108.dp
private val digitVisibleWidth = 54.dp
private val rankStroke = Color(0xFFB8B8BE)

/** Netflix-style Top 10: a huge outline-only rank number sits behind the left edge of each poster. */
@Composable
fun TopTenRail(title: String, items: List<RailItem>, onOpen: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TopTenTile(index + 1, item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun TopTenTile(rank: Int, item: RailItem, onClick: () -> Unit) {
    val digitCount = rank.toString().length
    val rankedItem = item.copy(rank = rank)
    Box(Modifier.width(posterWidth + digitVisibleWidth * digitCount)) {
        RankNumber(rank, Modifier.fillMaxHeight().width(digitVisibleWidth * digitCount + 24.dp).align(Alignment.CenterStart))
        PosterTile(rankedItem, onClick, Modifier.width(posterWidth).align(Alignment.CenterEnd))
    }
}

@Composable
private fun RankNumber(rank: Int, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = TextStyle(fontSize = 120.sp, fontWeight = FontWeight.Black, letterSpacing = (-10).sp)
    Canvas(modifier) {
        val outlineLayout = textMeasurer.measure(rank.toString(), baseStyle.copy(color = rankStroke, drawStyle = Stroke(width = 4.dp.toPx())))
        // Baseline on the poster's bottom edge, like Netflix. Drawing a measured layout keeps the
        // whole glyph; the string overload clips it to the canvas space below the origin.
        val origin = Offset(0f, size.height - outlineLayout.firstBaseline)
        drawText(outlineLayout, topLeft = origin)
    }
}
