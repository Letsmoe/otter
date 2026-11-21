package com.letsmoe.otter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Clock
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.SubtleFill
import com.letsmoe.otter.ui.components.pressable
import kotlinx.coroutines.CancellationException
import java.time.LocalTime

private val rowShape = RoundedCornerShape(16.dp)

private sealed interface TimeSlotState {
    data object Choosing : TimeSlotState
    data object Loading : TimeSlotState
    data object Failed : TimeSlotState
    data class Loaded(val suggestions: List<TimeSlotSuggestion>) : TimeSlotState
}

/** The home header's "I have X minutes": opens a sheet that shows what fits; [onPlay] gets the movie or episode id. */
@Composable
fun TimeSlotButton(onPlay: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.pressable { open = true }.size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Regular.Clock, contentDescription = "I have X minutes", modifier = Modifier.size(26.dp))
    }
    if (open) {
        GlassBottomSheet(onDismiss = { open = false }) {
            TimeSlotContent(onPlay = { mediaId ->
                open = false
                onPlay(mediaId)
            })
        }
    }
}

@Composable
private fun TimeSlotContent(onPlay: (String) -> Unit) {
    val choices = remember { timeSlotChoices(PlaybackSettings.bedtimeMinutes, minutesNow()) }
    var chosen by remember { mutableStateOf<TimeSlotChoice?>(null) }
    var state by remember { mutableStateOf<TimeSlotState>(TimeSlotState.Choosing) }
    LaunchedEffect(chosen) {
        val choice = chosen ?: return@LaunchedEffect
        state = TimeSlotState.Loading
        state = loadSuggestions(choice.minutes)
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("I have…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        ChoiceChips(choices, chosen, onChoose = { choice -> chosen = choice })
        TimeSlotResults(state, onPlay)
    }
}

private fun minutesNow(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

private suspend fun loadSuggestions(minutes: Int): TimeSlotState {
    val profileId = ProfileSession.activeProfile.value?.id ?: return TimeSlotState.Failed
    return try {
        TimeSlotState.Loaded(TimeSlotRepository.suggestions(profileId, minutes))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        TimeSlotState.Failed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(choices: List<TimeSlotChoice>, chosen: TimeSlotChoice?, onChoose: (TimeSlotChoice) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in choices) {
            ChoiceChip(choice.label, picked = choice == chosen, onClick = { onChoose(choice) })
        }
    }
}

@Composable
private fun ChoiceChip(label: String, picked: Boolean, onClick: () -> Unit) {
    var background = Color.White.copy(alpha = 0.08f)
    var textColor = Color.White.copy(alpha = 0.8f)
    if (picked) {
        background = Color.White
        textColor = Color.Black
    }
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = textColor,
        modifier = Modifier.pressable(onClick = onClick).clip(CircleShape).background(background).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun TimeSlotResults(state: TimeSlotState, onPlay: (String) -> Unit) {
    when (state) {
        is TimeSlotState.Choosing -> Hint("Pick how much time you have and Otter shows what fits.")
        is TimeSlotState.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
        }
        is TimeSlotState.Failed -> Hint("Couldn't look that up. Try again in a moment.")
        is TimeSlotState.Loaded -> Suggestions(state.suggestions, onPlay)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
}

@Composable
private fun Suggestions(suggestions: List<TimeSlotSuggestion>, onPlay: (String) -> Unit) {
    if (suggestions.isEmpty()) {
        Hint("Nothing fits that. Add a few short films to My List, or try a longer slot.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (suggestion in suggestions) {
            SuggestionRow(suggestion, onClick = { onPlay(suggestion.mediaId) })
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: TimeSlotSuggestion, onClick: () -> Unit) {
    val item = suggestion.item
    Row(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick).fillMaxWidth().background(SubtleFill, rowShape).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (item.posterUrl != null) {
                AsyncImage(item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(72.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle != null) {
                Text(item.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${suggestion.minutes} min · ${suggestion.reason}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
        }
    }
}
