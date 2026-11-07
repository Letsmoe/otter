package com.letsmoe.otter.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.letsmoe.otter.ui.components.TabHeader


/**
 * Home header: "For <profile>" with the shared actions (see [TabHeader]).
 * Transparent over the ambient gradient; takes a dark tint of [ambientColor] once the page
 * is [scrolled].
 */
@Composable
fun HomeHeader(
    profileName: String,
    ambientColor: Color,
    scrolled: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var targetBackground = Color.Transparent
    if (scrolled) {
        targetBackground = darkened(ambientColor, 0.45f).copy(alpha = 0.94f)
    }
    val background by animateColorAsState(targetBackground, tween(250), label = "headerBackground")
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TabHeader("For $profileName", onSearch)
    }
}
