package com.letsmoe.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.together.Ballot
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.search.combineFilters
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MILLISECONDS = 300L
private const val MAX_RESULTS = 8

/** A search for a title to put on the ballot; [canAdd] says whether a result may still be added. */
@Composable
fun SuggestSearch(ballot: Ballot, canAdd: (RailItem) -> Boolean, onAdd: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RailItem>>(emptyList()) }
    LaunchedEffect(query) {
        results = searchTitles(query)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QueryField(query) { typed -> query = typed }
        for (item in results) {
            ResultRow(item, enabled = canAdd(item) && !ballot.isSuggested(item.id), onAdd = { onAdd(item) })
        }
    }
}

// A newer query cancels this one while it waits, so only the last typed query is searched.
private suspend fun searchTitles(query: String): List<RailItem> {
    if (query.isBlank()) {
        return emptyList()
    }
    delay(SEARCH_DEBOUNCE_MILLISECONDS)
    OtterApi.awaitBackend()
    val filter = combineFilters(emptyList(), emptyMap())
    return MediaRepository.search(query, null, filter).take(MAX_RESULTS)
}

@Composable
private fun QueryField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(Modifier.fillMaxWidth().height(48.dp).frostedGlass(CircleShape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text("Search movies and shows", fontSize = 15.sp, color = faintText)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

@Composable
private fun ResultRow(item: RailItem, enabled: Boolean, onAdd: () -> Unit) {
    var label = "Add"
    if (!enabled) {
        label = "Added"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(innerGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (item.posterUrl != null) {
                AsyncImage(item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(60.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(resultDetail(item), fontSize = 12.sp, color = mutedText, maxLines = 1)
        }
        Box(
            Modifier
                .size(width = 72.dp, height = 36.dp)
                .pressable(enabled = enabled, onClick = onAdd)
                .background(addFill(enabled), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = addContent(enabled))
        }
    }
}

private fun addFill(enabled: Boolean): Color {
    if (enabled) {
        return Color.White
    }
    return Color.White.copy(alpha = 0.08f)
}

private fun addContent(enabled: Boolean): Color {
    if (enabled) {
        return Color.Black
    }
    return Color.White.copy(alpha = 0.5f)
}

private fun resultDetail(item: RailItem): String {
    var kind = "Movie"
    if (item.mediaType == MediaType.SERIES) {
        kind = "Series"
    }
    if (item.year == null) {
        return kind
    }
    return "$kind · ${item.year}"
}
