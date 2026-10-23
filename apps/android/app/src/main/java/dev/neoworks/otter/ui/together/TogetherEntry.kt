package dev.neoworks.otter.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.ArrowRight
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.regular.UsersThree
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.roomCodeFromText
import dev.neoworks.otter.ui.components.frostedGlass
import kotlinx.coroutines.launch

/** Not in a room: one big Start button, then a quiet join-with-code section. */
@Composable
internal fun TogetherEntry(initialCode: String, modifier: Modifier) {
    val busy by TogetherSession.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var codeText by remember { mutableStateOf(initialCode) }
    val parsedCode = roomCodeFromText(codeText)

    fun join(code: String) {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        scope.launch { TogetherSession.join(profileId, code) }
    }

    fun host() {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        scope.launch { TogetherSession.host(profileId) }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
        EntryHeader()
        ActionButton("Start a room", icon = PhosphorIcons.Bold.Plus, enabled = !busy, onClick = ::host)
        JoinSection(
            initialCode = initialCode,
            busy = busy,
            joinReady = parsedCode != null && !busy,
            onCodeChange = { code -> codeText = code },
            onJoin = { code -> join(code) },
            onJoinTyped = { if (parsedCode != null) join(parsedCode) },
        )
    }
}

@Composable
private fun EntryHeader() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(innerGap)) {
        Box(Modifier.size(64.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.UsersThree, contentDescription = null, modifier = Modifier.size(30.dp))
        }
        Text("Watch Together", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Watch in sync with friends on their own phones, with chat and reactions.",
            color = mutedText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun JoinSection(
    initialCode: String,
    busy: Boolean,
    joinReady: Boolean,
    onCodeChange: (String) -> Unit,
    onJoin: (String) -> Unit,
    onJoinTyped: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(innerGap)) {
        Text("or join with a code", color = faintText, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        CodeInput(initialCode, enabled = !busy, onChange = onCodeChange, onComplete = onJoin)
        ActionButton("Join room", style = ActionStyle.SOFT, icon = PhosphorIcons.Bold.ArrowRight, enabled = joinReady, onClick = onJoinTyped)
    }
}
