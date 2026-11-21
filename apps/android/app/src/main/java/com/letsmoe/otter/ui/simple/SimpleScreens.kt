package com.letsmoe.otter.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.letsmoe.otter.ui.media.MediaRepository

/** What she was watching, most recent first. */
@Composable
fun SimpleContinueScreen(onBack: () -> Unit, openPlayer: (String) -> Unit) {
    val viewModel = viewModel(key = "simple-continue") {
        SimpleTitlesViewModel { profileId -> MediaRepository.continueWatching(profileId, 30) }
    }
    SimpleTitlesPage("Continue watching", "You have not started anything yet. Go back, then open My List or Search.", viewModel, onBack, openPlayer)
}

/** Everything she saved to watch. */
@Composable
fun SimpleListScreen(onBack: () -> Unit, openPlayer: (String) -> Unit) {
    val viewModel = viewModel(key = "simple-list") {
        SimpleTitlesViewModel { profileId -> MediaRepository.watchlist(profileId) }
    }
    SimpleTitlesPage("My List", "Your list is empty. Someone can add shows and movies to it for you.", viewModel, onBack, openPlayer)
}

@Composable
private fun SimpleTitlesPage(
    heading: String,
    emptyText: String,
    viewModel: SimpleTitlesViewModel,
    onBack: () -> Unit,
    openPlayer: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val starter = rememberSimpleStarter(openPlayer)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        SimplePageTop(heading, onBack)
        SimpleTitleList(state.items, state.loaded, emptyText, starter, Modifier.weight(1f))
    }
}

/** A large search field; results appear as you type, each one a tap to watch. */
@Composable
fun SimpleSearchScreen(onBack: () -> Unit, openPlayer: (String) -> Unit) {
    val viewModel = viewModel(key = "simple-search") { SimpleSearchViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val starter = rememberSimpleStarter(openPlayer)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        SimplePageTop("Search", onBack)
        SimpleSearchField(state.query, viewModel::onQueryChange, viewModel::searchNow, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        SimpleTitleList(state.results, state.finished, searchEmptyText(state), starter, Modifier.weight(1f).padding(top = 8.dp))
    }
}

private fun searchEmptyText(state: SimpleSearchState): String {
    if (state.query.isBlank()) {
        return ""
    }
    return "Nothing found for \"${state.query.trim()}\". Try another spelling."
}

@Composable
private fun SimpleSearchField(query: String, onQueryChange: (String) -> Unit, onSearchNow: () -> Unit, modifier: Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(42.dp))
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = null, modifier = Modifier.size(40.dp).alpha(0.8f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Type a name here", fontSize = SimpleSize.button, color = Color.White.copy(alpha = 0.5f))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = SimpleSize.button, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchNow() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
    }
}
