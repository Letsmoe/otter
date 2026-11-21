package com.letsmoe.otter.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.pressable

/** Type sizes of simple mode: everything is at least this big. */
object SimpleSize {
    val heading = 34.sp
    val button = 30.sp
    val itemTitle = 26.sp
    val body = 20.sp
}

private val bigButtonShape = RoundedCornerShape(28.dp)

/** A wide, tall button with an icon and one plain word or two; the filled one is the main choice. */
@Composable
fun SimpleBigButton(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var fill = Color.White.copy(alpha = 0.08f)
    var contentColor = Color.White
    if (filled) {
        fill = Color.White
        contentColor = Color.Black
    }
    Row(
        modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick)
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .background(fill, bigButtonShape)
            .border(2.dp, Color.White.copy(alpha = 0.6f), bigButtonShape)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(48.dp))
        Text(label, fontSize = SimpleSize.button, fontWeight = FontWeight.Bold, color = contentColor)
    }
}

/** An arrow and the word "Back", so nobody has to know what the arrow means. */
@Composable
fun SimpleBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onBack)
            .heightIn(min = 64.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Bold.ArrowLeft, contentDescription = null, modifier = Modifier.size(32.dp))
        Text("Back", fontSize = SimpleSize.button, fontWeight = FontWeight.SemiBold)
    }
}

/** A page's top: the Back button, then what the page is called. */
@Composable
fun SimplePageTop(heading: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SimpleBackButton(onBack)
        Text(heading, fontSize = SimpleSize.heading, fontWeight = FontWeight.Black)
    }
}
