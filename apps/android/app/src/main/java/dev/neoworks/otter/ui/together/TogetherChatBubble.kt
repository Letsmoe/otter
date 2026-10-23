package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.together.ChatLine
import dev.neoworks.otter.ui.theme.OtterColors

private val ownBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
private val otherBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)

/** Mine on the right in the accent tint; others on the left with their avatar. */
@Composable
fun ChatBubble(line: ChatLine) {
    if (line.mine) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BubbleText(line.text, ownBubbleShape, OtterColors.accent.copy(alpha = 0.35f))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        PeerAvatar(line.from, 28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.from.name, fontSize = 11.sp, color = faintText, modifier = Modifier.padding(start = 4.dp))
            BubbleText(line.text, otherBubbleShape, Color.White.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun BubbleText(text: String, shape: RoundedCornerShape, fill: Color) {
    Text(
        text,
        fontSize = 14.sp,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .background(fill, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
