package dev.neoworks.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val labelShape = RoundedCornerShape(2.dp)
private val labelRed = Color(0xFFE50914)

/** Red square in the poster's top-right corner, like Netflix's "TOP 10" ribbon. */
@Composable
fun TopTenBadge(modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
            .background(labelRed)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TOP", fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text("10", fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

/** Small red tag along the poster's bottom edge: "Recently Added", "New Season". */
@Composable
fun PosterLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.3.sp,
        textAlign = TextAlign.Center,
        color = Color.White,
        modifier = modifier
            .clip(labelShape)
            .background(labelRed)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
