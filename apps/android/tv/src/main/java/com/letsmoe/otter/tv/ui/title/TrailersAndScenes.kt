package com.letsmoe.otter.tv.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Play
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.WIDE_CARD_WIDTH
import com.letsmoe.otter.ui.title.TitleVideo

private val MEDIA_CARD_SHAPE = RoundedCornerShape(8.dp)

/** The title's videos (trailers first) and then its scene stills, as 16:9 cards. */
@Composable
fun TrailersAndScenesRow(videos: List<TitleVideo>, sceneUrls: List<String>, navigation: TitleNavigation) {
    CardRow("Trailers & Scenes") {
        items(videos, key = { video -> "video-" + video.key }) { video ->
            VideoCard(video, onClick = { navigation.onPlayVideo(video.key) })
        }
        itemsIndexed(sceneUrls, key = { _, url -> "scene-$url" }) { index, url ->
            SceneCard(url, onClick = { navigation.onOpenScenes(sceneUrls, index) })
        }
    }
}

/** A video's thumbnail with a play mark, and its name and type under it. */
@Composable
private fun VideoCard(video: TitleVideo, onClick: () -> Unit) {
    Column(Modifier.width(WIDE_CARD_WIDTH)) {
        MediaCard(video.thumbnailUrl, onClick) {
            Box(
                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PhosphorIcons.Fill.Play, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }
        Text(
            video.name,
            fontSize = TvType.detail,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(video.type, fontSize = TvType.caption, color = TvColors.secondaryContent, maxLines = 1)
    }
}

@Composable
private fun SceneCard(imageUrl: String, onClick: () -> Unit) {
    Box(Modifier.width(WIDE_CARD_WIDTH)) {
        MediaCard(imageUrl, onClick) {}
    }
}

/** A focusable 16:9 image with [overlay] centred on it. */
@Composable
private fun MediaCard(imageUrl: String, onClick: () -> Unit, overlay: @Composable () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        shape = MEDIA_CARD_SHAPE,
        modifier = Modifier.width(WIDE_CARD_WIDTH).aspectRatio(16f / 9f),
    ) {
        Box(Modifier.fillMaxSize().background(TvColors.base300), contentAlignment = Alignment.Center) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            overlay()
        }
    }
}
