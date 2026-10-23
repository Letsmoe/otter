package dev.neoworks.otter.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Play
import dev.neoworks.otter.ui.components.frostedGlass

/** "Trailers & More": the trailer's thumbnail, playing inline on tap. */
@Composable
fun TrailerTab(trailerKey: String?, title: String, modifier: Modifier = Modifier) {
    if (trailerKey == null) {
        Text("No trailer available.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(vertical = 24.dp))
        return
    }
    var playing by remember(trailerKey) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
            if (playing) {
                var paused by remember(trailerKey) { mutableStateOf(false) }
                TrailerVideo(
                    trailerKey,
                    looping = false,
                    muted = false,
                    paused = paused,
                    modifier = Modifier.fillMaxSize().clickable { paused = !paused },
                )
            } else {
                TrailerThumbnail(trailerKey, onPlay = { playing = true })
            }
        }
        Text("Trailer: $title", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrailerThumbnail(trailerKey: String, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onPlay)) {
        AsyncImage("https://img.youtube.com/vi/$trailerKey/hqdefault.jpg", contentDescription = "Play trailer", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.Center).size(56.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Fill.Play, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}
