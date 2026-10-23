package dev.neoworks.otter.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import dev.neoworks.otter.ui.FailedMessage
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.ui.components.ActorRail
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.components.PosterSize
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.title.GlassIconButton

private const val BIOGRAPHY_COLLAPSED_LINES = 5

/** An actor or crew member: a full-bleed portrait with their name, biography, co-stars and filmography. */
@Composable
fun PersonScreen(personId: String, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit, onBack: () -> Unit) {
    val viewModel = viewModel(key = personId) { PersonViewModel(personId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coStars by viewModel.coStars.collectAsStateWithLifecycle()
    val recommended by viewModel.recommended.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = state) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is LoadState.Failed -> FailedMessage(current.message, onRetry = viewModel::load)
            is LoadState.Loaded -> PersonContent(current.value, PersonRows(recommended, coStars), onOpenItem, onOpenPerson)
        }
        Box(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)) {
            GlassIconButton(PhosphorIcons.Regular.ArrowLeft, "Back", onBack)
        }
    }
}

@Composable
private fun PersonContent(person: PersonDetail, rows: PersonRows, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "hero") { PersonHero(person, scroll = { heroScroll(listState) }) }
        if (!person.biography.isNullOrEmpty()) {
            item(key = "biography") { Biography(person.biography) }
        }
        item(key = "recommended") {
            PosterRail("Recommended with ${person.name}", rows.recommended, onOpenItem, size = PosterSize.LARGE)
        }
        item(key = "co-stars") {
            ActorRail("Often Acts With", rows.coStars, onOpen = { actor -> onOpenPerson(actor.personId) })
        }
        item(key = "filmography") { PosterRail("Filmography", person.filmography, onOpenItem) }
    }
}

/** The rows that load after the person: picks for the profile and frequent co-stars. */
private class PersonRows(val recommended: List<RailItem>, val coStars: List<ActorItem>)

private fun heroScroll(listState: LazyListState): Float {
    if (listState.firstVisibleItemIndex > 0) {
        return 0f
    }
    return listState.firstVisibleItemScrollOffset.toFloat()
}

// Tapping the biography unfolds all of it; tapping again folds it back.
@Composable
private fun Biography(biography: String) {
    var expanded by remember { mutableStateOf(false) }
    var maxLines = BIOGRAPHY_COLLAPSED_LINES
    if (expanded) {
        maxLines = Int.MAX_VALUE
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            biography,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .animateContentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
        )
    }
}
