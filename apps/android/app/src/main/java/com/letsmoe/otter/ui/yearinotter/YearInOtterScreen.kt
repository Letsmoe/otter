package com.letsmoe.otter.ui.yearinotter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.X
import com.letsmoe.otter.ui.FailedMessage
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.title.GlassIconButton
import kotlinx.coroutines.launch

private val storyBarHeight = 3.dp
private val topRoom = 52.dp
private val bottomRoom = 76.dp

/** Your year in Otter: a few full-screen cards to swipe through, each shareable as an image. */
@Composable
fun YearInOtterScreen(year: Int, onBack: () -> Unit) {
    val viewModel = viewModel(key = "year-$year") { YearInOtterViewModel(year) }
    val state by viewModel.cards.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = state) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is LoadState.Failed -> FailedMessage(current.message, onRetry = viewModel::load)
            is LoadState.Loaded -> CardPager(year, current.value, onBack)
        }
        if (state !is LoadState.Loaded) {
            Box(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)) {
                GlassIconButton(PhosphorIcons.Regular.X, "Close", onBack)
            }
        }
    }
}

@Composable
private fun CardPager(year: Int, cards: List<YearCard>, onBack: () -> Unit) {
    val pagerState = rememberPagerState { cards.size }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val insets = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + topRoom,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomRoom,
    )
    CompositionLocalProvider(LocalYearCardInsets provides insets) {
        HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
            YearCardView(cards[page], Modifier.fillMaxSize().recordedInto(layer, page == pagerState.currentPage))
        }
    }
    Box(Modifier.fillMaxSize()) {
        StoryBar(pagerState, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp))
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 24.dp)) {
            GlassIconButton(PhosphorIcons.Regular.X, "Close", onBack)
        }
        PillButton(
            "Share this card",
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp),
            enabled = true,
            filled = true,
            onClick = {
                scope.launch {
                    val image = layer.toImageBitmap().asAndroidBitmap()
                    shareCardImage(context, image, year, pagerState.currentPage + 1)
                }
            },
        )
    }
}

/** Draws the content as usual and keeps a copy in [layer], which later becomes the shared image. */
private fun Modifier.recordedInto(layer: GraphicsLayer, active: Boolean): Modifier {
    if (!active) {
        return this
    }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}

@Composable
private fun StoryBar(pagerState: PagerState, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (page in 0 until pagerState.pageCount) {
            var color = Color.White.copy(alpha = 0.3f)
            if (page <= pagerState.currentPage) {
                color = Color.White
            }
            Box(Modifier.weight(1f).height(storyBarHeight).clip(CircleShape).background(color))
        }
    }
}
