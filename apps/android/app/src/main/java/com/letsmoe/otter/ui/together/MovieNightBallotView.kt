package com.letsmoe.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.letsmoe.otter.api.posterUrl
import com.letsmoe.otter.together.Ballot
import com.letsmoe.otter.together.Suggestion
import com.letsmoe.otter.together.TogetherPeer
import com.letsmoe.otter.together.TogetherRoom
import com.letsmoe.otter.together.suggestedByText
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.theme.OtterColors

private val posterShape = RoundedCornerShape(8.dp)
private val barHeight = 4.dp

/** The titles on the ballot with their live counts; tapping one casts (or moves) this person's vote. */
@Composable
fun BallotView(ballot: Ballot, room: TogetherRoom, onVote: (String) -> Unit, modifier: Modifier = Modifier) {
    val counts = ballot.tally()
    val mine = ballot.votedFor(room.self.id)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (suggestion in ballot.suggestions) {
            val voters = room.peers.filter { peer -> ballot.votedFor(peer.id) == suggestion.mediaId }
            SuggestionRow(suggestion, room, counts.getValue(suggestion.mediaId), voters, selected = mine == suggestion.mediaId, onVote)
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    room: TogetherRoom,
    votes: Int,
    voters: List<TogetherPeer>,
    selected: Boolean,
    onVote: (String) -> Unit,
) {
    var frame = Color.White.copy(alpha = 0.08f)
    if (selected) {
        frame = Color.White
    }
    Column(
        Modifier
            .fillMaxWidth()
            .pressable { onVote(suggestion.mediaId) }
            .semantics { contentDescription = voteDescription(suggestion, votes, selected) }
            .subtleCard()
            .border(1.dp, frame, cardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(innerGap), verticalAlignment = Alignment.CenterVertically) {
            Poster(suggestion.posterPath)
            Column(Modifier.weight(1f)) {
                Text(suggestion.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detailLine(suggestion, room), fontSize = 12.sp, color = mutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VoteCount(votes, selected)
        }
        ShareBar(votes, room.peers.size, voters)
    }
}

@Composable
private fun Poster(posterPath: String?) {
    Box(Modifier.width(40.dp).height(60.dp).clip(posterShape).background(Color.White.copy(alpha = 0.06f))) {
        val url = posterUrl(posterPath)
        if (url != null) {
            AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().fillMaxWidth())
        }
    }
}

@Composable
private fun VoteCount(votes: Int, selected: Boolean) {
    var fill = Color.White.copy(alpha = 0.08f)
    var content = Color.White
    if (selected) {
        fill = Color.White
        content = Color.Black
    }
    Row(
        Modifier.height(32.dp).background(fill, CircleShape).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        }
        Text(votes.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

// How big a share of the room voted for the title, with who did.
@Composable
private fun ShareBar(votes: Int, people: Int, voters: List<TogetherPeer>) {
    var share = 0f
    if (people > 0) {
        share = votes.toFloat() / people
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(barHeight).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(OtterColors.success))
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            for (voter in voters) {
                PeerAvatar(voter, 20.dp, Modifier.border(1.dp, Color.Black, RoundedCornerShape(percent = 28)))
            }
        }
    }
}

private fun detailLine(suggestion: Suggestion, room: TogetherRoom): String {
    val by = suggestedByText(suggestion, room)
    if (suggestion.year == null) {
        return by
    }
    return "${suggestion.year} · $by"
}

private fun voteDescription(suggestion: Suggestion, votes: Int, selected: Boolean): String {
    var description = "Vote for ${suggestion.title}, $votes votes"
    if (votes == 1) {
        description = "Vote for ${suggestion.title}, 1 vote"
    }
    if (selected) {
        description += ", your vote"
    }
    return description
}
