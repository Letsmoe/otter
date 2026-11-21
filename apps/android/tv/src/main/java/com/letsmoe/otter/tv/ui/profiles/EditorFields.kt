package com.letsmoe.otter.tv.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.letsmoe.otter.profiles.LANGUAGE_CHOICES
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface

private val FIELD_SHAPE = RoundedCornerShape(10.dp)

/** The profile's name; selecting it opens the TV's on-screen keyboard. */
@Composable
fun NameField(name: String, onChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var borderColor = Color.Transparent
    if (focused) {
        borderColor = Color.White
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Name", fontSize = TvType.detail, color = TvColors.secondaryContent)
        BasicTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(fontSize = TvType.sectionTitle, color = TvColors.baseContent),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(TvColors.subtleFill, FIELD_SHAPE)
                        .border(2.dp, borderColor, FIELD_SHAPE)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("Name", fontSize = TvType.sectionTitle, color = TvColors.neutralContent)
                    }
                    innerField()
                }
            },
        )
    }
}

/** A whole-row on/off option: the row is what the D-pad focuses, the switch only shows the state. */
@Composable
fun ToggleRow(title: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    FocusableSurface(onClick = onToggle, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = TvType.body, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = TvType.detail, color = TvColors.secondaryContent)
            }
            SwitchLook(checked)
        }
    }
}

@Composable
private fun SwitchLook(checked: Boolean) {
    var track = Color.White.copy(alpha = 0.2f)
    var alignment = Alignment.CenterStart
    if (checked) {
        track = TvColors.success
        alignment = Alignment.CenterEnd
    }
    Box(
        modifier = Modifier.width(36.dp).height(20.dp).background(track, CircleShape).padding(3.dp),
        contentAlignment = alignment,
    ) {
        Box(Modifier.size(14.dp).background(Color.White, CircleShape))
    }
}

/** Audio languages in the profile's order; selecting one adds it at the end or removes it. */
@Composable
fun LanguageOptions(languages: List<String>, onToggle: (String) -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Languages", fontSize = TvType.body, fontWeight = FontWeight.SemiBold)
        Text(
            "Sources in these languages are tried first, in this order.",
            fontSize = TvType.detail,
            color = TvColors.secondaryContent,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(LANGUAGE_CHOICES, key = { choice -> choice.code }) { choice ->
                LanguageChip(choice.name, languages.indexOf(choice.code), onClick = { onToggle(choice.code) })
            }
        }
    }
}

/** [position] is the place in the profile's order, -1 when not picked. */
@Composable
private fun LanguageChip(name: String, position: Int, onClick: () -> Unit) {
    var container = TvColors.subtleFill
    var label = name
    var textColor = TvColors.secondaryContent
    if (position >= 0) {
        container = Color.White.copy(alpha = 0.24f)
        label = "${position + 1}. $name"
        textColor = TvColors.baseContent
    }
    FocusableSurface(onClick = onClick, shape = CircleShape, containerColor = container, ringWidth = 2.dp) {
        Text(
            label,
            fontSize = TvType.detail,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
