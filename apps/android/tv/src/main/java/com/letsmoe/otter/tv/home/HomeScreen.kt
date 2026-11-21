package com.letsmoe.otter.tv.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.api.backdropUrl
import com.letsmoe.otter.api.posterUrl
import com.letsmoe.otter.graphql.fragment.TitleCard
import com.letsmoe.otter.tv.R

// TV-safe area: content stays clear of the screen edges that some TVs crop.
private val SCREEN_PADDING_HORIZONTAL = 48.dp
private val SCREEN_PADDING_VERTICAL = 27.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = viewModel.state) {
            HomeState.Loading -> CenteredMessage("Starting Otter…")
            is HomeState.Failed -> FailedMessage(state.message, onRetry = viewModel::load)
            is HomeState.Loaded -> TrendingHome(state.trending)
        }
    }
}

@Composable
private fun TrendingHome(trending: List<TitleCard>) {
    var focusedTitle by remember { mutableStateOf(trending.firstOrNull()) }
    Box(modifier = Modifier.fillMaxSize()) {
        Backdrop(focusedTitle)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = SCREEN_PADDING_VERTICAL),
        ) {
            Wordmark()
            Spacer(modifier = Modifier.weight(1f))
            FocusedTitleName(focusedTitle)
            Text(
                "Trending",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = SCREEN_PADDING_HORIZONTAL, bottom = 12.dp),
            )
            PosterRow(trending, onFocus = { title -> focusedTitle = title })
        }
    }
}

@Composable
private fun Backdrop(title: TitleCard?) {
    if (title == null) {
        return
    }
    AsyncImage(
        model = backdropUrl(title.backdropPath),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    val fadeToBlack = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black)
    Box(modifier = Modifier.fillMaxSize().background(fadeToBlack))
}

@Composable
private fun Wordmark() {
    Image(
        painter = painterResource(R.drawable.otter_wordmark),
        contentDescription = "Otter",
        modifier = Modifier.padding(horizontal = SCREEN_PADDING_HORIZONTAL).height(32.dp),
    )
}

@Composable
private fun FocusedTitleName(title: TitleCard?) {
    if (title == null) {
        return
    }
    var name = title.title
    val year = title.year
    if (year != null) {
        name = "$name ($year)"
    }
    Text(
        name,
        fontSize = 34.sp,
        color = Color.White,
        modifier = Modifier.padding(start = SCREEN_PADDING_HORIZONTAL, bottom = 20.dp),
    )
}

@Composable
private fun PosterRow(titles: List<TitleCard>, onFocus: (TitleCard) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING_HORIZONTAL),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(titles, key = { title -> title.id }) { title ->
            PosterCard(title, onFocus)
        }
    }
}

@Composable
private fun PosterCard(title: TitleCard, onFocus: (TitleCard) -> Unit) {
    Card(
        onClick = {},
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocus(title)
                }
            },
    ) {
        AsyncImage(
            model = posterUrl(title.posterPath),
            contentDescription = title.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun FailedMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load titles", fontSize = 24.sp, color = Color.White)
        Text(message, fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) {
            Text("Try again")
        }
    }
}
