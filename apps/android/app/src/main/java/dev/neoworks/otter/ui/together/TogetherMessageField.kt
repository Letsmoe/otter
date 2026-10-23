package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.PaperPlaneRight
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.components.frostedGlass

private const val MESSAGE_LIMIT = 300

/** Pill-shaped glass field with a send button. */
@Composable
internal fun MessageField() {
    var draft by remember { mutableStateOf("") }

    fun send() {
        TogetherSession.sendChat(draft)
        draft = ""
    }

    Row(
        Modifier.fillMaxWidth().height(56.dp).frostedGlass(CircleShape).padding(start = 20.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) {
                Text("Message", fontSize = 15.sp, color = faintText)
            }
            BasicTextField(
                value = draft,
                onValueChange = { typed -> draft = typed.take(MESSAGE_LIMIT) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SendButton(ready = draft.isNotBlank()) { send() }
    }
}

@Composable
private fun SendButton(ready: Boolean, onClick: () -> Unit) {
    var fill = Color.White.copy(alpha = 0.08f)
    var tint = Color.White.copy(alpha = 0.35f)
    if (ready) {
        fill = Color.White
        tint = Color.Black
    }
    Box(
        Modifier.size(44.dp).pressable(enabled = ready, onClick = onClick).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Fill.PaperPlaneRight, "Send", tint = tint, modifier = Modifier.size(18.dp))
    }
}
