package dev.neoworks.otter.ui.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.SubtleFill
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4
private val KEY_ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "delete"))

/**
 * Full-screen 4-digit PIN entry with an on-screen keypad. Submits on the
 * fourth digit; [onComplete] answers with an error message or null.
 */
@Composable
fun PinPad(
    title: String,
    message: String,
    onBack: () -> Unit,
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
        if (busy) {
            return
        }
        if (key == "delete") {
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

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (avatarModel != null) {
                AsyncImage(avatarModel, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
            }
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            PinDots(digits.length, Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) })
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Keypad(onPress = ::press)
    }
}

private suspend fun shakeOnce(shake: Animatable<Float, *>) {
    repeat(2) {
        shake.animateTo(-8f, tween(60))
        shake.animateTo(8f, tween(60))
    }
    shake.animateTo(0f, tween(60))
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            var borderColor = Color.White.copy(alpha = 0.25f)
            if (isFilled) {
                borderColor = Color.White
            }
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isFilled) {
                    Text("•", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun Keypad(onPress: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in KEY_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in row) {
                    KeypadKey(key, onPress, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, onPress: (String) -> Unit, modifier: Modifier) {
    if (key.isEmpty()) {
        Spacer(modifier.height(64.dp))
        return
    }
    if (key == "delete") {
        TextButton(onClick = { onPress(key) }, modifier = modifier.height(64.dp), shape = CircleShape) {
            Icon(Icons.Filled.Clear, contentDescription = "Delete", tint = Color.White)
        }
        return
    }
    TextButton(
        onClick = { onPress(key) },
        modifier = modifier.height(64.dp).clip(CircleShape).background(SubtleFill),
        shape = CircleShape,
    ) {
        Text(key, fontSize = 24.sp, color = Color.White)
    }
}

