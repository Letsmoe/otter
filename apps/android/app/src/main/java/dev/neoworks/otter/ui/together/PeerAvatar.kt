package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.together.TogetherPeer

/** A Watch Together participant's profile icon; dims with a spinner on top while their player is loading. */
@Composable
fun PeerAvatar(peer: TogetherPeer, size: Dp, modifier: Modifier = Modifier, buffering: Boolean = false) {
    Box(modifier.size(size).clip(RoundedCornerShape(percent = 28))) {
        AsyncImage(
            model = avatarImage(peer.avatar, peer.name),
            contentDescription = peer.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.42f),
                    strokeWidth = 2.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}
