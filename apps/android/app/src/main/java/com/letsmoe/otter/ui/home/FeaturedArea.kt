package com.letsmoe.otter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.ui.media.FeaturedItem
import com.letsmoe.otter.ui.navigation.bottomBarClearance

// Space between the Play / My List buttons and the floating tab bar.
private val gapAboveBottomBar = 16.dp

// Short (landscape) windows: keep a usable poster and let the page scroll to the buttons.
private val minAreaHeight = 420.dp

/**
 * Top of a home page: the category chips and the featured poster on a blurred copy of it, filling
 * the screen between the header and the tab bar so the poster is as large as the buttons allow.
 */
@Composable
fun FeaturedArea(
    featured: FeaturedItem?,
    inMyList: Boolean,
    ambientColor: Color,
    topPadding: Dp,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
    chips: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(featuredAreaHeight(topPadding))) {
        PosterGlow(featured?.item?.posterUrl, ambientColor, reachAbove = topPadding, modifier = Modifier.matchParentSize())
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            chips()
            FeaturedPreview(
                featured = featured,
                inMyList = inMyList,
                onOpen = onOpen,
                onPlay = onPlay,
                onToggleMyList = onToggleMyList,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun featuredAreaHeight(topPadding: Dp): Dp {
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(LocalDensity.current) { windowHeightPx.toDp() }
    val height = windowHeight - topPadding - bottomBarClearance() - gapAboveBottomBar
    return height.coerceAtLeast(minAreaHeight)
}
