package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.together.ROOM_CODE_LENGTH
import dev.neoworks.otter.together.roomCodeCharacters
import dev.neoworks.otter.together.roomCodeFromText

private val boxShape = RoundedCornerShape(12.dp)
private const val GROUP_SIZE = ROOM_CODE_LENGTH / 2

/**
 * One box per room-code character, in two groups of four. A single hidden field takes the typing,
 * so pasting a code or a whole invite text fills every box.
 */
@Composable
fun CodeInput(initial: String, enabled: Boolean, onChange: (String) -> Unit, onComplete: (String) -> Unit) {
    var code by remember { mutableStateOf(roomCodeCharacters(initial).take(ROOM_CODE_LENGTH).joinToString("")) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = code,
        onValueChange = { typed ->
            code = codeFromTyped(typed)
            onChange(code)
            if (code.length == ROOM_CODE_LENGTH) {
                onComplete(code)
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        modifier = Modifier.fillMaxWidth().onFocusChanged { state -> focused = state.isFocused },
        decorationBox = { CodeBoxes(code, focused) },
    )
}

// A pasted invite text fills the whole code; anything else keeps its valid characters.
private fun codeFromTyped(typed: String): String {
    val pasted = roomCodeFromText(typed)
    if (pasted != null && typed.length > ROOM_CODE_LENGTH) {
        return pasted
    }
    return roomCodeCharacters(typed).take(ROOM_CODE_LENGTH).joinToString("")
}

@Composable
private fun CodeBoxes(code: String, focused: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (index in 0 until ROOM_CODE_LENGTH) {
            if (index == GROUP_SIZE) {
                Box(Modifier.width(6.dp))
            }
            CodeBox(character = code.getOrNull(index), isNext = focused && index == code.length)
        }
    }
}

@Composable
private fun CodeBox(character: Char?, isNext: Boolean) {
    var fill = Color.White.copy(alpha = 0.06f)
    var borderColor = Color.White.copy(alpha = 0.08f)
    if (character != null) {
        fill = Color.White.copy(alpha = 0.12f)
    }
    if (isNext) {
        borderColor = Color.White.copy(alpha = 0.7f)
    }
    Box(
        Modifier.size(width = 36.dp, height = 52.dp).background(fill, boxShape).border(1.dp, borderColor, boxShape),
        contentAlignment = Alignment.Center,
    ) {
        if (character != null) {
            Text(character.toString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}
