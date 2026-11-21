package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.theme.TvType

/** The top bar's tabs; Search has no media type, the others are home categories. */
enum class HomeTab(val label: String, val mediaType: MediaType?) {
    SEARCH("Search", null),
    MOVIES("Movies", MediaType.MOVIE),
    SHOWS("Shows", MediaType.SERIES),
}

/** The top bar's tabs; like every TV tab row, moving focus onto a tab switches to it. */
@Composable
fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val tabs = HomeTab.entries
    TabRow(selectedTabIndex = tabs.indexOf(selected)) {
        for (tab in tabs) {
            Tab(selected = tab == selected, onFocus = { onSelect(tab) }) {
                Text(
                    tab.label,
                    fontSize = TvType.body,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
    }
}
