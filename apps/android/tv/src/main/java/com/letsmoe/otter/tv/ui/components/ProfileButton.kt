package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.profiles.avatarImage

private val AVATAR_SHAPE = RoundedCornerShape(10.dp)

/** The active profile's icon in the top right; selecting it goes back to "Who's watching?". */
@Composable
fun ProfileButton() {
    val profile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val activeProfile = profile ?: return
    FocusableSurface(
        onClick = ProfileSession::logout,
        shape = AVATAR_SHAPE,
        focusedScale = 1.15f,
        modifier = Modifier.size(40.dp),
    ) {
        AsyncImage(
            model = avatarImage(activeProfile.avatarUrl, activeProfile.name),
            contentDescription = "Switch profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(AVATAR_SHAPE),
        )
    }
}
