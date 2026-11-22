package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.theme.TvType

/** The top bar's tabs; Search has no media type and shows as an icon, the others are home categories. */
enum class HomeTab(val label: String, val mediaType: MediaType?) {
    MOVIES("Movies", MediaType.MOVIE),
    SHOWS("Shows", MediaType.SERIES),
    SEARCH("Search", null),
}

private val BAR_FILL = Color.Black.copy(alpha = 0.45f)
// The selected tab's pill while focus is down in the page.
private val RESTING_PILL = Color.White.copy(alpha = 0.22f)

/**
 * The tabs in a translucent capsule, the selected one on a light pill. Like every TV tab row,
 * moving focus onto a tab switches to it.
 */
@Composable
fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val tabs = HomeTab.entries
    val selectedIndex = tabs.indexOf(selected)
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        separator = {},
        indicator = { tabPositions, doesTabRowHaveFocus ->
            TabRowDefaults.PillIndicator(
                currentTabPosition = tabPositions[selectedIndex],
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = Color.White,
                inactiveColor = RESTING_PILL,
            )
        },
        modifier = Modifier.background(BAR_FILL, CircleShape).padding(4.dp),
    ) {
        for (tab in tabs) {
            Tab(
                selected = tab == selected,
                onFocus = { onSelect(tab) },
                colors = tabColors(),
            ) {
                TabLabel(tab)
            }
        }
    }
}

@Composable
private fun tabColors() = TabDefaults.pillIndicatorTabColors(
    contentColor = Color.White.copy(alpha = 0.75f),
    selectedContentColor = Color.White,
    focusedContentColor = Color.Black,
    focusedSelectedContentColor = Color.Black,
)

@Composable
private fun TabLabel(tab: HomeTab) {
    val padding = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    if (tab == HomeTab.SEARCH) {
        Icon(PhosphorIcons.Bold.MagnifyingGlass, contentDescription = tab.label, modifier = padding.size(16.dp))
        return
    }
    Text(tab.label, fontSize = TvType.body, fontWeight = FontWeight.SemiBold, modifier = padding)
}
