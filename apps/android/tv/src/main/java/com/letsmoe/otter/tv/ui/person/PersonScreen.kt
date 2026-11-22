package com.letsmoe.otter.tv.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.FailedNotice
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.FullBleedBackdrop
import com.letsmoe.otter.tv.ui.components.HeroRowsPage
import com.letsmoe.otter.tv.ui.components.POSTER_ROW_SPACE
import com.letsmoe.otter.tv.ui.components.PageRow
import com.letsmoe.otter.tv.ui.components.PosterPreviewCard
import com.letsmoe.otter.tv.ui.components.REGULAR_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.tv.ui.home.ActorRow
import com.letsmoe.otter.tv.ui.home.topBarFade
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.person.PersonDetail
import com.letsmoe.otter.ui.person.PersonViewModel

private val PHOTO_WIDTH = 150.dp
private val PHOTO_HEIGHT = 225.dp
private val INFO_WIDTH = 460.dp
private const val BIOGRAPHY_FOLDED_LINES = 4
private const val BIOGRAPHY_UNFOLDED_LINES = 14

/** A cast or crew member: photo, name and biography, then their titles and who they work with. */
@Composable
fun PersonScreen(personId: String, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel(key = "tv-person-$personId") { PersonViewModel(personId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recommended by viewModel.recommended.collectAsStateWithLifecycle()
    val coStars by viewModel.coStars.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            LoadState.Loading -> Unit
            is LoadState.Failed -> FailedNotice(
                "Couldn't open this person",
                current.message,
                onRetry = viewModel::load,
                modifier = Modifier.align(Alignment.Center),
            )
            is LoadState.Loaded -> LoadedPerson(current.value, PersonRows(recommended, coStars, onOpenItem, onOpenPerson))
        }
        TopBar(modifier = topBarFade())
    }
}

/** What the rows under a person show and do. */
private class PersonRows(
    val recommended: List<RailItem>,
    val coStars: List<ActorItem>,
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

@Composable
private fun LoadedPerson(person: PersonDetail, rows: PersonRows) {
    HeroRowsPage(
        firstRowSpace = POSTER_ROW_SPACE,
        rows = personPageRows(person, rows),
        // The backdrop of their best-known title sets the mood behind them.
        backdrop = { modifier -> FullBleedBackdrop(person.filmography.firstOrNull()?.backdropUrl, modifier) },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            PersonHeader(person)
        }
    }
}

private fun personPageRows(person: PersonDetail, rows: PersonRows): List<PageRow> {
    val pageRows = mutableListOf<PageRow>()
    if (person.filmography.isNotEmpty()) {
        pageRows.add(PageRow("known-for") { TitleRow("Known For", person.filmography, rows.onOpenItem) })
    }
    if (rows.recommended.isNotEmpty()) {
        pageRows.add(PageRow("recommended") { TitleRow("Recommended for You", rows.recommended, rows.onOpenItem) })
    }
    if (rows.coStars.isNotEmpty()) {
        pageRows.add(PageRow("co-stars") { ActorRow("Often Works With", rows.coStars, rows.onOpenPerson) })
    }
    return pageRows
}

@Composable
private fun TitleRow(title: String, items: List<RailItem>, onOpenItem: (RailItem) -> Unit) {
    CardRow(title) {
        items(items, key = { item -> item.id }) { item ->
            PosterPreviewCard(item, REGULAR_POSTER_WIDTH, onClick = { onOpenItem(item) })
        }
    }
}

/** Photo on the left; name, facts and the biography (focused first, OK unfolds it) beside it. */
@Composable
private fun PersonHeader(person: PersonDetail) {
    Row(
        modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AsyncImage(
            model = person.photoUrl,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(PHOTO_WIDTH)
                .height(PHOTO_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(TvColors.base300),
        )
        Column(Modifier.width(INFO_WIDTH), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(person.name, fontSize = TvType.heroTitle, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(personFacts(person), fontSize = TvType.detail, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent, maxLines = 1)
            Biography(person.biography)
        }
    }
}

// "Acting  ·  Born 1974 (51)  ·  Los Angeles, California, USA"
private fun personFacts(person: PersonDetail): String {
    return listOfNotNull(person.knownForDepartment, person.lifespan, person.placeOfBirth).joinToString("  ·  ")
}

/** Takes focus when the page opens, so the page always has something focused; OK unfolds it. */
@Composable
private fun Biography(biography: String?) {
    var unfolded by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var text = biography
    if (text.isNullOrBlank()) {
        text = "No biography yet."
    }
    var maxLines = BIOGRAPHY_FOLDED_LINES
    if (unfolded) {
        maxLines = BIOGRAPHY_UNFOLDED_LINES
    }
    FocusableSurface(
        onClick = { unfolded = !unfolded },
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = Color.Transparent,
        focusedContainerColor = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    ) {
        Text(
            text,
            fontSize = TvType.detail,
            lineHeight = 17.sp,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).animateContentSize(),
        )
    }
}
