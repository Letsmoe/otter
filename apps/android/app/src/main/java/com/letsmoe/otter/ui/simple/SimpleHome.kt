package com.letsmoe.otter.ui.simple

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.BookmarkSimple
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.profiles.PreferenceChange
import com.letsmoe.otter.profiles.ProfilePreferences
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.profiles.PinPad
import com.letsmoe.otter.ui.profiles.checkProfilePin

/** The first page: her name and three big buttons. */
@Composable
fun SimpleHomeScreen(onContinue: () -> Unit, onMyList: () -> Unit, onSearch: () -> Unit) {
    val profile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    BackHandler(enabled = leaving) { leaving = false }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(greeting(profile), fontSize = SimpleSize.heading, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp))
        SimpleBigButton("Continue watching", PhosphorIcons.Fill.Play, filled = true, onClick = onContinue)
        SimpleBigButton("My List", PhosphorIcons.Regular.BookmarkSimple, filled = false, onClick = onMyList)
        SimpleBigButton("Search", PhosphorIcons.Regular.MagnifyingGlass, filled = false, onClick = onSearch)
        Spacer(Modifier.height(24.dp))
        LeaveSimpleModeButton(onLongPress = { leaving = true })
    }
    if (leaving) {
        LeaveSimpleModeFlow(profile, onDone = { leaving = false })
    }
}

private fun greeting(profile: ProfileFields?): String {
    if (profile == null || profile.name.isBlank()) {
        return "Hello"
    }
    return "Hello, ${profile.name}"
}

// Small and quiet, and only a long press opens it, so it is not hit by accident.
@Composable
private fun LeaveSimpleModeButton(onLongPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { Toasts.push("Press and hold to open.") }, onLongClick = onLongPress)
            .padding(vertical = 16.dp)
            .alpha(0.45f),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.LockSimple, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("For helpers: press and hold", fontSize = 14.sp)
    }
}

/** A PIN pad when the profile has a PIN, else a plain question; either one switches simple mode off. */
@Composable
private fun LeaveSimpleModeFlow(profile: ProfileFields?, onDone: () -> Unit) {
    if (profile == null) {
        return
    }
    if (leaveCheckFor(profile.hasPin) == LeaveCheck.CONFIRMATION) {
        LeaveConfirmation(onStay = onDone, onLeave = { leaveSimpleMode(onDone) })
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PinPad(
            title = "Leave simple mode",
            message = "Enter the PIN of ${profile.name}.",
            onBack = onDone,
            onComplete = { pin -> checkPinThenLeave(profile, pin, onDone) },
        )
    }
}

private suspend fun checkPinThenLeave(profile: ProfileFields, pin: String, onDone: () -> Unit): String? {
    val failure = checkProfilePin(profile.id, pin)
    if (failure != null) {
        return failure
    }
    leaveSimpleMode(onDone)
    return null
}

private fun leaveSimpleMode(onDone: () -> Unit) {
    ProfilePreferences.change(PreferenceChange(simpleMode = false))
    Toasts.push("Simple mode is off.", ToastKind.SUCCESS)
    onDone()
}

@Composable
private fun LeaveConfirmation(onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Leave simple mode?", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = { Text("The full app has many more buttons. Only continue if you are the one helping.", fontSize = 18.sp) },
        confirmButton = { TextButton(onClick = onLeave) { Text("Leave", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = onStay) { Text("Stay", fontSize = 18.sp, fontWeight = FontWeight.Bold) } },
    )
}
