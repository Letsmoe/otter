package com.letsmoe.otter.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ThumbsDown
import com.adamglin.phosphoricons.bold.ThumbsUp
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.adamglin.phosphoricons.fill.ThumbsDown as ThumbsDownFill
import com.adamglin.phosphoricons.fill.ThumbsUp as ThumbsUpFill

/** The three ratings the phone offers, on the backend's 1-10 scale. */
enum class RatingChoice(val label: String, val value: Double, val icon: ImageVector, val chosenIcon: ImageVector) {
    NOT_FOR_ME("Not for me", 2.0, PhosphorIcons.Bold.ThumbsDown, PhosphorIcons.Fill.ThumbsDownFill),
    LIKE("I like this", 7.5, PhosphorIcons.Bold.ThumbsUp, PhosphorIcons.Fill.ThumbsUpFill),
    LOVE("Love this!", 10.0, PhosphorIcons.Bold.Heart, PhosphorIcons.Fill.HeartFill),
}

/**
 * A thumb showing the current [rating]; selecting it opens the three choices in its place, with
 * focus on the current one. Choosing one (the current one again clears it) or moving focus away
 * closes them. [thumbModifier] applies to the closed thumb only, not to the open choices.
 */
@Composable
fun RatingButton(rating: Double?, onRate: (Double) -> Unit, modifier: Modifier = Modifier, thumbModifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    // After a choice the thumb takes focus back; the choices it replaces had it.
    var thumbTakesFocus by remember { mutableStateOf(false) }
    val current = RatingChoice.entries.firstOrNull { choice -> choice.value == rating }
    AnimatedContent(open, modifier = modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ratingButton") { showChoices ->
        if (showChoices) {
            RatingChoices(current, onChoose = { value ->
                thumbTakesFocus = true
                open = false
                onRate(value)
            }, onLeave = {
                thumbTakesFocus = false
                open = false
            })
        } else {
            ClosedThumb(current, takeFocus = thumbTakesFocus, onOpen = { open = true }, modifier = thumbModifier)
        }
    }
}

@Composable
private fun ClosedThumb(current: RatingChoice?, takeFocus: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val thumb = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (takeFocus) {
            thumb.requestFocus()
        }
    }
    var icon = PhosphorIcons.Bold.ThumbsUp
    var label = "Rate"
    if (current != null) {
        icon = current.chosenIcon
        label = current.label
    }
    IconButton(icon, label, onClick = onOpen, modifier = modifier.focusRequester(thumb))
}

@Composable
private fun RatingChoices(current: RatingChoice?, onChoose: (Double) -> Unit, onLeave: () -> Unit) {
    val first = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { first.requestFocus() }
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(3.dp)
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    hadFocus = true
                } else if (hadFocus) {
                    onLeave()
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (choice in RatingChoice.entries) {
            var icon = choice.icon
            var choiceModifier: Modifier = Modifier
            if (choice == current) {
                icon = choice.chosenIcon
            }
            if (choice == focusFirst(current)) {
                choiceModifier = Modifier.focusRequester(first)
            }
            IconButton(icon, choice.label, onClick = { onChoose(choice.value) }, modifier = choiceModifier)
        }
    }
}

// The current rating, or "I like this" when there is none.
private fun focusFirst(current: RatingChoice?): RatingChoice {
    if (current != null) {
        return current
    }
    return RatingChoice.LIKE
}
