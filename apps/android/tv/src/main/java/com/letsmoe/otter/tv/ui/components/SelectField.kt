package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.Check
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType

/** One choice of a [SelectField]: what it stands for and what it says. */
data class SelectOption(val value: String, val label: String)

private val LIST_WIDTH = 320.dp
private val LIST_MAX_HEIGHT = 380.dp
private val ROW_SHAPE = RoundedCornerShape(8.dp)

/**
 * A dropdown for the remote: a row with [label] and the chosen option; OK opens the options in a
 * list over the screen with focus on the chosen one. Choosing one closes it, Back cancels.
 * [description] goes under the label when given.
 */
@Composable
fun SelectField(
    label: String,
    options: List<SelectOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    FocusableSurface(onClick = { open = true }, focusedScale = 1.02f, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldLabel(label, description, Modifier.weight(1f))
            Text(chosenLabel(options, selected), fontSize = TvType.detail, color = TvColors.secondaryContent, maxLines = 1)
            Icon(PhosphorIcons.Bold.CaretDown, contentDescription = null, modifier = Modifier.size(12.dp))
        }
    }
    if (open) {
        OptionsDialog(label, options, selected, onDismiss = { open = false }, onChoose = { value ->
            open = false
            onSelect(value)
        })
    }
}

// A value missing from the options (e.g. a stored language the list lacks) shows as itself.
private fun chosenLabel(options: List<SelectOption>, selected: String): String {
    val chosen = options.firstOrNull { option -> option.value == selected }
    if (chosen == null) {
        return selected
    }
    return chosen.label
}

@Composable
private fun FieldLabel(label: String, description: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = TvType.detail, fontWeight = FontWeight.SemiBold)
        if (description != null) {
            Text(description, fontSize = TvType.caption, color = TvColors.secondaryContent)
        }
    }
}

@Composable
private fun OptionsDialog(title: String, options: List<SelectOption>, selected: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val selectedIndex = maxOf(options.indexOfFirst { option -> option.value == selected }, 0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val chosenRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { chosenRow.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(LIST_WIDTH)
                .background(TvColors.base300, RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontSize = TvType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(options, key = { _, option -> option.value }) { index, option ->
                    var rowModifier: Modifier = Modifier
                    if (index == selectedIndex) {
                        rowModifier = Modifier.focusRequester(chosenRow)
                    }
                    OptionRow(option, chosen = option.value == selected, onClick = { onChoose(option.value) }, modifier = rowModifier)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: SelectOption, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FocusableSurface(onClick = onClick, shape = ROW_SHAPE, focusedScale = 1f, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(option.label, fontSize = TvType.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            CheckMark(chosen, size = 14.dp)
        }
    }
}

@Composable
private fun CheckMark(chosen: Boolean, size: Dp) {
    if (!chosen) {
        return
    }
    Box {
        Icon(PhosphorIcons.Bold.Check, contentDescription = "Chosen", modifier = Modifier.size(size))
    }
}
