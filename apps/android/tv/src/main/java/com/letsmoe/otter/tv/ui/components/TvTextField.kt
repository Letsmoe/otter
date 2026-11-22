package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType

private val FIELD_SHAPE = RoundedCornerShape(10.dp)

/** What the field shows and how it types; [compact] is a lower, fully rounded pill with smaller text. */
class TextFieldLook(
    val placeholder: String,
    val keyboardOptions: KeyboardOptions,
    val icon: ImageVector? = null,
    val compact: Boolean = false,
) {
    fun shape(): Shape {
        if (compact) {
            return CircleShape
        }
        return FIELD_SHAPE
    }

    fun fontSize(): TextUnit {
        if (compact) {
            return TvType.body
        }
        return TvType.sectionTitle
    }

    fun verticalPadding(): Dp {
        if (compact) {
            return 7.dp
        }
        return 11.dp
    }
}

/**
 * A one-line text field for the remote. Android TV opens its keyboard whenever a text field gets
 * focus, so passing over the field with the D-pad focuses a plain box instead; OK turns it into the
 * text field with the keyboard open, and leaving it or the keyboard's action key turns it back.
 */
@Composable
fun TvTextField(value: String, onValueChange: (String) -> Unit, look: TextFieldLook, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    // After the keyboard's action key, focus goes back to the box rather than wherever Android puts it.
    var refocusBox by remember { mutableStateOf(false) }
    Box(modifier) {
        if (editing) {
            EditingField(value, onValueChange, look, onDone = {
                refocusBox = true
                editing = false
            }, onLeft = { editing = false })
            return@Box
        }
        FieldBox(value, look, refocus = refocusBox, onRefocused = { refocusBox = false }, onClick = { editing = true })
    }
}

@Composable
private fun FieldBox(value: String, look: TextFieldLook, refocus: Boolean, onRefocused: () -> Unit, onClick: () -> Unit) {
    val boxFocus = remember { FocusRequester() }
    LaunchedEffect(refocus) {
        if (refocus) {
            boxFocus.requestFocus()
            onRefocused()
        }
    }
    FocusableSurface(
        onClick = onClick,
        shape = look.shape(),
        focusedScale = 1f,
        focusedContainerColor = TvColors.subtleFill,
        modifier = Modifier.fillMaxWidth().focusRequester(boxFocus),
    ) {
        FieldDecoration(value, look, borderColor = Color.Transparent) {
            Text(value, fontSize = look.fontSize(), color = TvColors.baseContent, maxLines = 1)
        }
    }
}

@Composable
private fun EditingField(
    value: String,
    onValueChange: (String) -> Unit,
    look: TextFieldLook,
    onDone: () -> Unit,
    onLeft: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        fieldFocus.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = look.fontSize(), color = TvColors.baseContent),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = look.keyboardOptions,
        keyboardActions = KeyboardActions(onAny = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fieldFocus)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                }
                if (hadFocus && !state.isFocused) {
                    onLeft()
                }
            },
        decorationBox = { innerField -> FieldDecoration(value, look, borderColor = Color.White, content = innerField) },
    )
}

/** The field's fill, ring, icon and placeholder around [content]. */
@Composable
private fun FieldDecoration(value: String, look: TextFieldLook, borderColor: Color, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TvColors.subtleFill, look.shape())
            .border(2.dp, borderColor, look.shape())
            .padding(horizontal = 16.dp, vertical = look.verticalPadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = look.icon
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TvColors.secondaryContent, modifier = Modifier.size(16.dp))
        }
        Box {
            if (value.isEmpty()) {
                Text(look.placeholder, fontSize = look.fontSize(), color = TvColors.neutralContent)
            }
            content()
        }
    }
}
