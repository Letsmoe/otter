package dev.neoworks.otter.ui.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Info
import com.adamglin.phosphoricons.regular.Plus
import dev.neoworks.otter.ui.components.ActorRail
import dev.neoworks.otter.ui.components.GlassBottomSheet
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.title.TrailerVideo
import dev.neoworks.otter.ui.title.rememberTrailerState

private val mediaShape = RoundedCornerShape(16.dp)
private val buttonShape = RoundedCornerShape(12.dp)
private const val OVERVIEW_LINES = 4

/** The long-press preview: the muted trailer, the essentials, and the way into the title page. */
@Composable
fun TitlePreviewSheet(onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val item by TitlePreview.item.collectAsStateWithLifecycle()
    val current = item ?: return
    val viewModel = viewModel(key = "preview-${current.id}") { TitlePreviewViewModel(current.id) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    GlassBottomSheet(onDismiss = TitlePreview::close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PreviewMedia(detail, fallbackImage = current.posterUrl)
            PreviewText(detail, fallbackTitle = current.title)
            PreviewCast(detail) { personId ->
                TitlePreview.close()
                onOpenPerson(personId)
            }
            PreviewButtons(
                inMyList = detail?.inMyList == true,
                onDetails = {
                    TitlePreview.close()
                    onOpenItem(current)
                },
                onToggleMyList = viewModel::toggleMyList,
            )
        }
    }
}

@Composable
private fun PreviewMedia(detail: PreviewDetail?, fallbackImage: String?) {
    val trailer = rememberTrailerState(detail?.trailerKey)
    var targetImageAlpha = 1f
    if (trailer.visible) {
        targetImageAlpha = 0f
    }
    val imageAlpha by animateFloatAsState(targetImageAlpha, tween(600), label = "previewImage")
    var image = fallbackImage
    if (detail?.backdropUrl != null) {
        image = detail.backdropUrl
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(mediaShape).background(Color.Black)) {
        if (trailer.trailerKey != null) {
            TrailerVideo(trailer.trailerKey, looping = true, muted = true, modifier = Modifier.fillMaxSize(), onReady = { trailer.visible = true })
        }
        AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(imageAlpha))
    }
}

@Composable
private fun PreviewText(detail: PreviewDetail?, fallbackTitle: String) {
    var title = fallbackTitle
    if (detail != null) {
        title = detail.title
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail == null) {
            return@Column
        }
        Text(detail.facts.joinToString(" · "), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        ExpandableOverview(detail.overview)
    }
}

// Tapping the description shows all of it, tapping again folds it back.
@Composable
private fun ExpandableOverview(overview: String) {
    var expanded by remember(overview) { mutableStateOf(false) }
    var maxLines = OVERVIEW_LINES
    if (expanded) {
        maxLines = Int.MAX_VALUE
    }
    Text(
        overview,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .animateContentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
    )
}

@Composable
private fun PreviewCast(detail: PreviewDetail?, onOpenPerson: (String) -> Unit) {
    if (detail == null) {
        return
    }
    // The sheet already pads its content; the rail brings its own 12dp.
    ActorRail("Cast", detail.cast, onOpen = { actor -> onOpenPerson(actor.personId) }, modifier = Modifier.offset(x = (-12).dp))
}

@Composable
private fun PreviewButtons(inMyList: Boolean, onDetails: () -> Unit, onToggleMyList: () -> Unit) {
    var myListIcon = PhosphorIcons.Regular.Plus
    if (inMyList) {
        myListIcon = PhosphorIcons.Regular.Check
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PreviewButton("Details", PhosphorIcons.Regular.Info, Color.White, Color.Black, onDetails, Modifier.weight(1f))
        PreviewButton("My List", myListIcon, Color.White.copy(alpha = 0.12f), Color.White, onToggleMyList, Modifier.weight(1f))
    }
}

@Composable
private fun PreviewButton(label: String, icon: ImageVector, background: Color, content: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.height(48.dp).clip(buttonShape).background(background).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, color = content, fontWeight = FontWeight.SemiBold)
    }
}
