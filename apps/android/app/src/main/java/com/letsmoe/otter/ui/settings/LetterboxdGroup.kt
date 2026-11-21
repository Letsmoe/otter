package com.letsmoe.otter.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.PlainTextField
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.theme.OtterColors

private val zipTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
private val mutedColor = Color.White.copy(alpha = 0.45f)

/** Brings a Letterboxd account in: an export ZIP once, and the public diary feed from then on. */
@Composable
fun LetterboxdGroup(viewModel: LetterboxdViewModel = viewModel()) {
    val footer = "Otter reads your Letterboxd export and your public diary feed. Letterboxd only gives API access " +
        "to approved apps, so Otter can't send your ratings or diary entries back to Letterboxd."
    SettingsGroup("Letterboxd", footer = footer) {
        ImportRow(viewModel)
        ImportResult(viewModel.progress)
        SettingsDivider()
        FollowRows(viewModel)
    }
}

@Composable
private fun ImportRow(viewModel: LetterboxdViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFrom(context.contentResolver, uri)
        }
    }
    val running = viewModel.progress.phase == ImportPhase.RUNNING
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Import from Letterboxd", fontSize = 14.sp)
            Text(
                "Pick the ZIP from Letterboxd's Settings, Import & Export. Watched films, ratings, diary dates and the watchlist go to this profile.",
                fontSize = 12.sp,
                color = mutedColor,
            )
        }
        PillButton("Choose file", Modifier, enabled = !running && !viewModel.reading, filled = true) {
            picker.launch(zipTypes)
        }
    }
}

@Composable
private fun ImportResult(progress: ImportProgress) {
    when (progress.phase) {
        ImportPhase.RUNNING -> ImportRunning(progress)
        ImportPhase.DONE -> ImportDone(progress)
        ImportPhase.FAILED -> Text(progress.error.orEmpty(), fontSize = 12.sp, color = OtterColors.error, modifier = Modifier.padding(bottom = 14.dp))
        ImportPhase.IDLE -> Unit
    }
}

@Composable
private fun ImportRunning(progress: ImportProgress) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { progress.progressFraction() }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.15f))
        Text(progress.progressText(), fontSize = 12.sp, color = mutedColor)
    }
}

@Composable
private fun ImportDone(progress: ImportProgress) {
    var showMissing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Import finished", fontSize = 13.sp)
        for (line in progress.summaryLines()) {
            Text(line, fontSize = 12.sp, color = mutedColor)
        }
        if (progress.notMatched.isNotEmpty()) {
            MissingFilmsToggle(showMissing, onToggle = { showMissing = !showMissing })
        }
        if (showMissing) {
            MissingFilms(progress)
        }
    }
}

@Composable
private fun MissingFilmsToggle(shown: Boolean, onToggle: () -> Unit) {
    var label = "Show films not matched"
    if (shown) {
        label = "Hide films not matched"
    }
    Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.pressable(onClick = onToggle).padding(vertical = 6.dp))
}

@Composable
private fun MissingFilms(progress: ImportProgress) {
    for (film in progress.notMatched) {
        Text("${film.label()}: ${film.reason}", fontSize = 12.sp, color = mutedColor)
    }
    val unlisted = progress.unmatchedCount - progress.notMatched.size
    if (unlisted > 0) {
        Text("and ${count(unlisted, "more film")}", fontSize = 12.sp, color = mutedColor)
    }
}

@Composable
private fun FollowRows(viewModel: LetterboxdViewModel) {
    val account = viewModel.account
    if (account == null) {
        ConnectRow(viewModel)
        return
    }
    FollowedRow(account, viewModel)
}

@Composable
private fun ConnectRow(viewModel: LetterboxdViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Follow your diary", fontSize = 14.sp)
        Text("Every few hours Otter reads the latest entries of your public diary: new watches and ratings.", fontSize = 12.sp, color = mutedColor)
        PlainTextField(viewModel.username, { typed -> viewModel.username = typed }, "Letterboxd username", keyboardType = KeyboardType.Uri)
        PillButton("Follow", Modifier.fillMaxWidth(), enabled = viewModel.username.isNotBlank() && !viewModel.syncing, filled = true, onClick = viewModel::connect)
    }
}

@Composable
private fun FollowedRow(account: FollowedAccount, viewModel: LetterboxdViewModel) {
    var detailColor = mutedColor
    if (account.lastError != null) {
        detailColor = OtterColors.error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Following ${account.username}", fontSize = 14.sp)
            Text(account.syncLine(), fontSize = 12.sp, color = detailColor)
        }
        PillButton("Sync now", Modifier, enabled = !viewModel.syncing, filled = false, onClick = viewModel::syncNow)
        PillButton("Stop", Modifier, enabled = !viewModel.syncing, filled = false, onClick = viewModel::disconnect)
    }
}
