package dev.neoworks.otter.ui.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.regular.Copy
import dev.neoworks.otter.together.TogetherRoom
import dev.neoworks.otter.together.formatRoomCode
import dev.neoworks.otter.together.inviteText
import dev.neoworks.otter.ui.theme.OtterColors
import kotlinx.coroutines.delay

private const val COPIED_MILLISECONDS = 1600L

/** Inside a room: status, the code to share, who is there and what plays. */
@Composable
fun TogetherRoomView(room: TogetherRoom, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Watch Together", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            StatusChip(room.status)
        }
        CodeHero(room.code)
        PeersCard(room)
        NowPlayingCard()
    }
}

@Composable
private fun CodeHero(code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MILLISECONDS)
            copied = false
        }
    }
    var codeColor = Color.White
    if (copied) {
        codeColor = OtterColors.success
    }
    Column(Modifier.fillMaxWidth().subtleCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(innerGap)) {
        Text("ROOM CODE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = faintText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            formatRoomCode(code),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            letterSpacing = 6.sp,
            color = codeColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(innerGap)) {
            CopyButton(copied, Modifier.weight(1f)) {
                copyToClipboard(context, inviteText(code))
                copied = true
            }
            ActionButton("Share", Modifier.weight(1f), icon = PhosphorIcons.Bold.ShareNetwork, onClick = { shareInvite(context, code) })
        }
    }
}

@Composable
private fun CopyButton(copied: Boolean, modifier: Modifier, onCopy: () -> Unit) {
    if (copied) {
        ActionButton("Copied", modifier, ActionStyle.SOFT, PhosphorIcons.Bold.Check, onClick = onCopy)
        return
    }
    ActionButton("Copy", modifier, ActionStyle.SOFT, PhosphorIcons.Regular.Copy, onClick = onCopy)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Watch Together", text))
}

private fun shareInvite(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inviteText(code))
    context.startActivity(Intent.createChooser(send, "Watch Together").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
