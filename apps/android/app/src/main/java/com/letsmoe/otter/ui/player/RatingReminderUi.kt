package com.letsmoe.otter.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "How was it?" with the rating buttons, bottom left, for the last seconds of a film. */
@Composable
fun BoxScope.RatingReminderOverlay(visible: Boolean, mediaId: String, controlsVisible: Boolean) {
    val modifier = Modifier.align(Alignment.BottomStart)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(start = 24.dp, bottom = bottomOffset(controlsVisible))
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        RatingReminderCard(mediaId)
    }
}

/** The question with the three rating buttons; shared by the player and the TV remote. */
@Composable
fun RatingReminderCard(mediaId: String, modifier: Modifier = Modifier) {
    Column(modifier.playerGlass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How was it?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
        RatingTrio(mediaId)
    }
}
