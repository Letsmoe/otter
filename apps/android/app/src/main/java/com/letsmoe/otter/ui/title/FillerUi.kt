package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.graphql.type.FillerKind
import com.letsmoe.otter.ui.components.OtterSwitch

private val fillerMutedColor = Color.White.copy(alpha = 0.55f)

/** How an episode row says what a filler list calls it; null for canon and episodes no list covers. */
fun fillerLabelOf(kind: FillerKind?): String? {
    return when (kind) {
        FillerKind.FILLER -> "Filler"
        FillerKind.MIXED -> "Mixed canon/filler"
        else -> null
    }
}

/** "Skip filler episodes" for one series: Play, the next episode and autoplay pass them over. */
@Composable
fun SkipFillerRow(skipFiller: Boolean, fillerCount: Int, onChange: (Boolean) -> Unit) {
    if (fillerCount == 0) {
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Skip filler episodes", fontSize = 14.sp)
            Text("$fillerCount filler episodes; mixed ones stay", fontSize = 12.sp, color = fillerMutedColor)
        }
        OtterSwitch(skipFiller, onChange)
    }
}
