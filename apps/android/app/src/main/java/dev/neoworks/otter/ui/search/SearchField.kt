package dev.neoworks.otter.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.XCircle
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.theme.OtterColors

/** Back button and a glass search field that takes focus on open. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back", modifier = Modifier.size(24.dp))
        }
        Row(
            Modifier.weight(1f).height(44.dp).frostedGlass(CircleShape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(PhosphorIcons.Regular.MagnifyingGlass, null, Modifier.size(22.dp).alpha(0.7f))
            SearchInput(query, onQueryChange, focusRequester, Modifier.weight(1f))
            if (query.isNotBlank()) {
                Icon(
                    PhosphorIcons.Fill.XCircle,
                    contentDescription = "Clear",
                    modifier = Modifier.size(22.dp).alpha(0.7f).clickable(onClick = onClear),
                )
            }
        }
    }
}

@Composable
private fun SearchInput(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text("Search movies and shows", fontSize = 16.sp, color = Color.White.copy(alpha = 0.4f))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = OtterColors.baseContent, fontSize = 16.sp),
            cursorBrush = SolidColor(OtterColors.baseContent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}
