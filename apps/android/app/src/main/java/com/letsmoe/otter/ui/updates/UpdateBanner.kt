package com.letsmoe.otter.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.updates.AppUpdater
import com.letsmoe.otter.updates.UpdateState

/** A card at the top while a newer release is available, downloading or ready to install. */
@Composable
fun UpdateBanner() {
    LaunchedEffect(Unit) { AppUpdater.checkForUpdate() }
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    if (state == UpdateState.None) {
        return
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BannerContent(state)
        }
    }
}

@Composable
private fun BannerContent(state: UpdateState) {
    val context = LocalContext.current
    when (state) {
        is UpdateState.Available -> {
            BannerText("Otter ${state.release.displayName()} is available", "Download it and install over this version.")
            BannerButtons("Update", onPrimary = { AppUpdater.downloadAndInstall(context, state.release) })
        }
        is UpdateState.Downloading -> {
            BannerText("Downloading Otter ${state.release.displayName()}", "${(state.progress * 100).toInt()} %")
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.ReadyToInstall -> {
            BannerText("Otter ${state.release.displayName()} is ready", "Allow installs from Otter if Android asks.")
            BannerButtons("Install", onPrimary = { AppUpdater.install(context, state.apk) })
        }
        is UpdateState.Failed -> {
            BannerText("Update failed", state.message)
            BannerButtons("Try again", onPrimary = { AppUpdater.downloadAndInstall(context, state.release) })
        }
        UpdateState.None -> Unit
    }
}

@Composable
private fun BannerText(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun BannerButtons(primaryLabel: String, onPrimary: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Later", Modifier.weight(1f), enabled = true, filled = false, onClick = AppUpdater::dismiss)
        PillButton(primaryLabel, Modifier.weight(1f), enabled = true, filled = true, onClick = onPrimary)
    }
}
