package dev.neoworks.otter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

/** A bare text field for settings rows: no box, a dim placeholder. */
@Composable
fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    textColor: Color = Color.White,
) {
    var transformation: VisualTransformation = VisualTransformation.None
    if (isPassword) {
        transformation = PasswordVisualTransformation()
    }
    Box(modifier) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Modifier.fillMaxWidth(): Modifier = this.then(Modifier.fillMaxWidth())
