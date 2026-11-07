package com.letsmoe.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.adamglin.phosphoricons.fill.ThumbsDown as ThumbsDownFill
import com.adamglin.phosphoricons.fill.ThumbsUp as ThumbsUpFill
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.ThumbsDown
import com.adamglin.phosphoricons.regular.ThumbsUp
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.TitleActions
import kotlinx.coroutines.launch

private class RatingOption(val label: String, val value: Double, val icon: ImageVector, val activeIcon: ImageVector)

private val ratingOptions = listOf(
    RatingOption("Not for me", 2.0, PhosphorIcons.Regular.ThumbsDown, PhosphorIcons.Fill.ThumbsDownFill),
    RatingOption("I like this", 7.5, PhosphorIcons.Regular.ThumbsUp, PhosphorIcons.Fill.ThumbsUpFill),
    RatingOption("Love this!", 10.0, PhosphorIcons.Regular.Heart, PhosphorIcons.Fill.HeartFill),
)

/** Three glass rating buttons (Not for me, I like this, Love this) for the player. Mirrors RatingTrio.svelte. */
@Composable
fun RatingTrio(mediaId: String, modifier: Modifier = Modifier, onInteract: () -> Unit = {}) {
    var rating by remember(mediaId) { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    val profileId = ProfileSession.activeProfile.value?.id
    LaunchedEffect(mediaId, profileId) {
        if (profileId != null) {
            rating = fetchUserRating(mediaId, profileId)
        }
    }

    // Choosing the current rating again clears it.
    fun rate(value: Double) {
        onInteract()
        if (profileId == null) {
            return
        }
        scope.launch {
            if (rating == value) {
                TitleActions.clearRating(profileId, mediaId)
                rating = null
                return@launch
            }
            TitleActions.setRating(profileId, mediaId, value)
            rating = value
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in ratingOptions) {
            RatingButton(option, selected = option.value == rating) { rate(option.value) }
        }
    }
}

@Composable
private fun RatingButton(option: RatingOption, selected: Boolean, onClick: () -> Unit) {
    var background = Modifier.playerGlass(CircleShape)
    var icon = option.icon
    var tint = Color.White
    if (selected) {
        background = Modifier.background(Color.White, CircleShape)
        icon = option.activeIcon
        tint = Color.Black
    }
    Box(Modifier.pressable(onClick = onClick).size(44.dp).then(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = option.label, tint = tint, modifier = Modifier.size(20.dp))
    }
}
