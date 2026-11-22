package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
 * moving focus onto a tab switches to it, so focus coming up from the page always lands on the
 * selected tab, never on whichever tab happens to be closest. Down goes to [page]: the page's list
 * reaches up behind the bar, so Compose's own search doesn't count it as below the tabs.
 */
@Composable
fun HomeTabs(selected: HomeTab, page: FocusRequester, onSelect: (HomeTab) -> Unit) {
    val tabs = HomeTab.entries
    val selectedIndex = tabs.indexOf(selected)
    val selectedTab = remember { FocusRequester() }
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        // TabRow places one separator between each pair of tabs, so it must lay something out.
        separator = { Spacer(Modifier.width(4.dp)) },
        indicator = { tabPositions, doesTabRowHaveFocus ->
            TabRowDefaults.PillIndicator(
                currentTabPosition = tabPositions[selectedIndex],
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = Color.White,
                inactiveColor = RESTING_PILL,
            )
        },
        modifier = Modifier
            // A focus group that enters on the selected tab.
            .focusRestorer(selectedTab)
            .background(BAR_FILL, CircleShape)
            .padding(4.dp),
    ) {
        for (tab in tabs) {
            var tabModifier: Modifier = Modifier.focusProperties { down = page }
            if (tab == selected) {
                tabModifier = tabModifier.focusRequester(selectedTab)
            }
            Tab(
                selected = tab == selected,
                onFocus = { onSelect(tab) },
                colors = tabColors(),
                modifier = tabModifier,
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
