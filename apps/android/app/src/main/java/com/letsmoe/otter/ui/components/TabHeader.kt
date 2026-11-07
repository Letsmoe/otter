package com.letsmoe.otter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.letsmoe.otter.ui.television.TelevisionButton
import com.letsmoe.otter.ui.together.TogetherButton

/**
 * The header every main page shares: its title, then Play on TV, Watch Together and search.
 * Without [onSearch] (the search page itself) the search button is left out.
 */
@Composable
fun TabHeader(title: String, onSearch: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SharedHeaderActions(onSearch)
    }
}

/** Play on TV, Watch Together and, given [onSearch], search: reachable from every main page. */
@Composable
fun RowScope.SharedHeaderActions(onSearch: (() -> Unit)?) {
    TelevisionButton(iconSize = 26.dp)
    TogetherButton(iconSize = 26.dp)
    if (onSearch == null) {
        return
    }
    var tapCount by remember { mutableIntStateOf(0) }
    val openSearch = {
        tapCount += 1
        onSearch()
    }
    Box(Modifier.pressable(onClick = openSearch).size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = "Search", modifier = Modifier.wiggleOnChange(tapCount).size(26.dp))
    }
}
