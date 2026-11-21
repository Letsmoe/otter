package com.letsmoe.otter.tv.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.profiles.AVATAR_SETS
import com.letsmoe.otter.profiles.AvatarSet
import com.letsmoe.otter.profiles.PRESET_AVATARS
import com.letsmoe.otter.profiles.avatarImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.ui.components.FocusableSurface

private val AVATAR_SIZE = 112.dp
private val AVATAR_SHAPE = RoundedCornerShape(18.dp)

/** Every preset icon, one row per set; focus starts on the profile's current icon. */
@Composable
fun AvatarChooser(selected: String, onPick: (String) -> Unit) {
    val selectedAvatar = remember { FocusRequester() }
    // A remote URL or an old value isn't in the sets: start on the first preset instead.
    var focusTarget = PRESET_AVATARS.first()
    if (selected in PRESET_AVATARS) {
        focusTarget = selected
    }
    // Starts scrolled to the target's set (item 0 is the heading), so its row is composed and focusable.
    val targetSetIndex = AVATAR_SETS.indexOfFirst { set -> focusTarget in set.avatars }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetSetIndex + 1)
    LaunchedEffect(Unit) { selectedAvatar.requestFocus() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choose an icon", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("Back keeps the current one.", fontSize = 16.sp, color = TvColors.secondaryContent)
            }
        }
        items(AVATAR_SETS, key = { set -> set.key }) { set ->
            AvatarSetRow(set, focusTarget, selectedAvatar, onPick)
        }
    }
}

@Composable
private fun AvatarSetRow(set: AvatarSet, focusTarget: String, focusRequester: FocusRequester, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(set.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(set.avatars, key = { avatarId -> avatarId }) { avatarId ->
                var tileModifier: Modifier = Modifier
                if (avatarId == focusTarget) {
                    tileModifier = Modifier.focusRequester(focusRequester)
                }
                FocusableSurface(
                    onClick = { onPick(avatarId) },
                    shape = AVATAR_SHAPE,
                    focusedScale = 1.12f,
                    modifier = tileModifier.size(AVATAR_SIZE),
                ) {
                    AsyncImage(
                        model = avatarImage(avatarId, ""),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(AVATAR_SHAPE),
                    )
                }
            }
        }
    }
}
