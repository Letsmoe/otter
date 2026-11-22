package com.letsmoe.otter.tv.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface

// Clears the top bar above the list.
private val LIST_TOP = 96.dp
private val LIST_WIDTH = 620.dp

/** The home's Settings tab: one list of what the TV uses, grouped like the phone's settings. */
@Composable
fun SettingsScreen() {
    val viewModel = viewModel { TvSettingsViewModel() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = TvSpacing.screenHorizontal, top = LIST_TOP, bottom = TvSpacing.screenVertical),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GroupTitle("Titles")
        ValueRow("Title language", "Names and descriptions of titles loaded from now on", viewModel.titleLanguageName(), viewModel::nextTitleLanguage)
        GroupTitle("Streaming")
        SwitchRow("Require VPN", "No lookups or playback without a VPN", viewModel.requireVpn, viewModel::toggleRequireVpn)
        GroupTitle("Playback")
        SwitchRow("Skip intros automatically", "Jumps over intros and recaps once", viewModel.skipIntros, viewModel::toggleSkipIntros)
        SwitchRow("Even volume", "Quieter loud scenes, clearer quiet ones", viewModel.evenVolume, viewModel::toggleEvenVolume)
        GroupTitle("Privacy")
        SwitchRow("Send error reports", "Crashes and failures go to the developers", viewModel.errorReporting, viewModel::toggleErrorReporting)
        GroupTitle("Profile")
        ValueRow("Switch profile", "Back to who's watching", ProfileSession.activeProfile.value?.name.orEmpty(), ProfileSession::logout)
        VersionLine()
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        fontSize = TvType.detail,
        fontWeight = FontWeight.SemiBold,
        color = TvColors.secondaryContent,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** A row that cycles or opens something on select; [value] shows on its right. */
@Composable
private fun ValueRow(label: String, description: String, value: String, onClick: () -> Unit) {
    SettingRow(label, description, onClick) {
        Text(value, fontSize = TvType.body, color = TvColors.secondaryContent)
    }
}

/** A row that turns something on or off on select. */
@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onClick: () -> Unit) {
    SettingRow(label, description, onClick) {
        SwitchIndicator(checked)
    }
}

@Composable
private fun SettingRow(label: String, description: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    FocusableSurface(onClick = onClick, focusedScale = 1.02f, modifier = Modifier.width(LIST_WIDTH)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = TvType.body, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = TvType.detail, color = TvColors.secondaryContent)
            }
            trailing()
        }
    }
}

private val SWITCH_WIDTH = 36.dp
private val SWITCH_HEIGHT = 20.dp
private val KNOB_SIZE = 14.dp

@Composable
private fun SwitchIndicator(checked: Boolean) {
    var trackColor = Color.White.copy(alpha = 0.2f)
    var knobOffset = 3.dp
    if (checked) {
        trackColor = TvColors.accent
        knobOffset = SWITCH_WIDTH - KNOB_SIZE - 3.dp
    }
    val offset by animateDpAsState(knobOffset, label = "switchKnob")
    Box(
        modifier = Modifier.width(SWITCH_WIDTH).height(SWITCH_HEIGHT).background(trackColor, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(start = offset).size(KNOB_SIZE).background(Color.White, CircleShape))
    }
}

@Composable
private fun VersionLine() {
    val context = LocalContext.current
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    Text(
        "Otter $versionName",
        fontSize = TvType.caption,
        color = TvColors.neutralContent,
        modifier = Modifier.padding(top = 18.dp),
    )
}
