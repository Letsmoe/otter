package com.letsmoe.otter.tv.ui.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Backspace
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4
private val KEY_SIZE = 48.dp
private const val DELETE_KEY = "delete"
private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", DELETE_KEY),
)
private val DIGIT_KEYS = mapOf(
    Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
    Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
    Key.NumPad0 to "0", Key.NumPad1 to "1", Key.NumPad2 to "2", Key.NumPad3 to "3", Key.NumPad4 to "4",
    Key.NumPad5 to "5", Key.NumPad6 to "6", Key.NumPad7 to "7", Key.NumPad8 to "8", Key.NumPad9 to "9",
)

/**
 * 4-digit PIN entry: the remote's number keys or the on-screen keypad. Submits on
 * the fourth digit; [onComplete] answers with an error message or null.
 */
@Composable
fun PinEntry(
    title: String,
    message: String,
    onComplete: suspend (String) -> String?,
    avatarModel: String? = null,
) {
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        busy = true
        scope.launch {
            val result = onComplete(digits)
            busy = false
            digits = ""
            if (result != null) {
                error = result
                shakeOnce(shake)
            }
        }
    }

    fun press(key: String) {
        if (busy || key.isEmpty()) {
            return
        }
        if (key == DELETE_KEY) {
            digits = digits.dropLast(1)
            return
        }
        if (digits.length >= PIN_LENGTH) {
            return
        }
        error = ""
        digits += key
        if (digits.length == PIN_LENGTH) {
            submit()
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event -> handleRemoteKey(event, ::press) },
        horizontalArrangement = Arrangement.spacedBy(72.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (avatarModel != null) {
                AsyncImage(
                    avatarModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(title, fontSize = TvType.screenTitle, fontWeight = FontWeight.Bold)
            Text(message, fontSize = TvType.body, color = TvColors.secondaryContent)
            PinDots(digits.length, Modifier.padding(top = 8.dp).offset { IntOffset(shake.value.dp.roundToPx(), 0) })
            Text(error, fontSize = TvType.detail, color = TvColors.error)
        }
        Keypad(onPress = ::press)
    }
}

/** Number keys type, the remote's delete key removes a digit; everything else (D-pad, back) passes on. */
private fun handleRemoteKey(event: KeyEvent, press: (String) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    if (event.key == Key.Backspace || event.key == Key.Delete) {
        press(DELETE_KEY)
        return true
    }
    val digit = DIGIT_KEYS[event.key] ?: return false
    press(digit)
    return true
}

private suspend fun shakeOnce(shake: Animatable<Float, *>) {
    for (offset in listOf(14f, -12f, 8f, -6f, 0f)) {
        shake.animateTo(offset, tween(55))
    }
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (index in 0 until PIN_LENGTH) {
            var fill = Color.Transparent
            if (index < filled) {
                fill = Color.White
            }
            Box(
                Modifier
                    .size(14.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .background(fill, CircleShape),
            )
        }
    }
}

@Composable
private fun Keypad(onPress: (String) -> Unit) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstKey.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in KEYPAD_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (key in row) {
                    var keyModifier: Modifier = Modifier
                    if (key == "1") {
                        keyModifier = Modifier.focusRequester(firstKey)
                    }
                    KeypadKey(key, onPress, keyModifier)
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, onPress: (String) -> Unit, modifier: Modifier) {
    if (key.isEmpty()) {
        Box(Modifier.size(KEY_SIZE))
        return
    }
    FocusableSurface(onClick = { onPress(key) }, shape = CircleShape, modifier = modifier.size(KEY_SIZE)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (key == DELETE_KEY) {
                Icon(PhosphorIcons.Bold.Backspace, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            } else {
                Text(key, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
