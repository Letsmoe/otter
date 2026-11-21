package com.letsmoe.otter.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.FilmSlate
import com.adamglin.phosphoricons.regular.Plus
import com.letsmoe.otter.together.Ballot
import com.letsmoe.otter.together.MAX_SUGGESTIONS_PER_PERSON
import com.letsmoe.otter.together.MovieNight
import com.letsmoe.otter.together.TogetherRoom
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.together.isOpen
import com.letsmoe.otter.together.posterPathOf
import com.letsmoe.otter.together.suggestionsLeft
import com.letsmoe.otter.together.voteStatus
import com.letsmoe.otter.together.voteSummary
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.RailItem

/** The movie night vote in its own tall sheet, stacked on the Watch Together sheet. */
@Composable
fun MovieNightSheet() {
    val sheetOpen by MovieNight.sheetOpen.collectAsStateWithLifecycle()
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val currentRoom = room
    if (!sheetOpen || currentRoom == null || !room.isOpen()) {
        return
    }
    val ballot by MovieNight.ballot.collectAsStateWithLifecycle()
    GlassBottomSheet(onDismiss = MovieNight::closeSheet, fullHeight = true) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            MovieNightContent(ballot, currentRoom)
        }
    }
}

/** Header, the ballot, a way to suggest and the footer with what the vote waits for. */
@Composable
fun MovieNightContent(ballot: Ballot, room: TogetherRoom) {
    var searching by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(innerGap)) {
        Text("Movie night", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Everyone can suggest up to 3 titles. Vote on your phone; the winner starts for the whole room.",
            fontSize = 13.sp,
            color = mutedText,
        )
        if (ballot.isEmpty) {
            EmptyBallot()
        } else {
            BallotView(ballot, room, onVote = MovieNight::vote)
        }
        SuggestArea(ballot, room, searching, onToggle = { searching = !searching }, onAdded = { searching = false })
        VoteFooter(ballot, room)
    }
}

@Composable
private fun EmptyBallot() {
    Column(Modifier.fillMaxWidth().padding(vertical = sectionGap), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(PhosphorIcons.Regular.FilmSlate, null, tint = faintText, modifier = Modifier.size(32.dp))
        Text("No titles yet", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = mutedText)
        Text("Suggest something to watch.", fontSize = 13.sp, color = faintText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SuggestArea(ballot: Ballot, room: TogetherRoom, searching: Boolean, onToggle: () -> Unit, onAdded: () -> Unit) {
    val left = suggestionsLeft(ballot, room)
    if (left == 0) {
        Text("You have suggested $MAX_SUGGESTIONS_PER_PERSON titles.", fontSize = 13.sp, color = faintText)
        return
    }
    if (!searching) {
        ActionButton("Suggest a title ($left left)", style = ActionStyle.SOFT, icon = PhosphorIcons.Regular.Plus, onClick = onToggle)
        return
    }
    SuggestSearch(
        ballot,
        canAdd = { item -> ballot.canSuggest(room.self.id, item.id) },
        onAdd = { item ->
            suggest(item)
            onAdded()
        },
    )
}

private fun suggest(item: RailItem) {
    MovieNight.suggest(item.id, item.title, item.year, posterPathOf(item.posterUrl))
}

@Composable
private fun VoteFooter(ballot: Ballot, room: TogetherRoom) {
    Text(voteStatus(ballot, room), fontSize = 12.sp, color = faintText)
    if (!room.hosting) {
        return
    }
    ActionButton("Close vote and play the winner", enabled = ballot.winner() != null, onClick = MovieNight::closeVote)
}

/** Opens the movie night sheet: what is on the ballot and how many have voted. */
@Composable
fun MovieNightCard(modifier: Modifier = Modifier) {
    val ballot by MovieNight.ballot.collectAsStateWithLifecycle()
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val currentRoom = room ?: return
    Row(
        modifier
            .fillMaxWidth()
            .pressable { MovieNight.openSheet() }
            .subtleCard()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(innerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.FilmSlate, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Movie night vote", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(voteSummary(ballot, currentRoom), fontSize = 13.sp, color = mutedText, maxLines = 1)
        }
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = faintText, modifier = Modifier.size(18.dp))
    }
}
