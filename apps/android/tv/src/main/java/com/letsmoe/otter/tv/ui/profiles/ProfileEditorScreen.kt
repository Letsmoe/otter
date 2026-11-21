package com.letsmoe.otter.tv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Trash
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.profiles.avatarImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.ui.profiles.EditorStep
import com.letsmoe.otter.ui.profiles.ProfileEditorViewModel

private val AVATAR_SHAPE = RoundedCornerShape(28.dp)

/** Add or edit a profile; [profile] is null for a new one. */
@Composable
fun ProfileEditorScreen(profile: ProfileFields?, onDone: () -> Unit) {
    val editor = viewModel(key = "profile-editor-${profile?.id}") { ProfileEditorViewModel(profile) }
    BackHandler(enabled = editor.step != EditorStep.FORM) { editor.step = EditorStep.FORM }
    when (editor.step) {
        EditorStep.FORM -> EditorForm(editor, onDone)
        EditorStep.AVATAR -> AvatarChooser(
            selected = editor.avatarId,
            onPick = { avatarId ->
                editor.avatarId = avatarId
                editor.step = EditorStep.FORM
            },
        )
        EditorStep.PIN_CREATE -> PinEntry(
            title = "Profile Lock",
            message = "Choose a 4-digit PIN.",
            onComplete = editor::onPinCreated,
            avatarModel = avatarImage(editor.avatarId, editor.name),
        )
        EditorStep.PIN_CONFIRM -> PinEntry(
            title = "Profile Lock",
            message = "Enter the PIN again.",
            onComplete = editor::onPinConfirmed,
            avatarModel = avatarImage(editor.avatarId, editor.name),
        )
    }
}

@Composable
private fun EditorForm(editor: ProfileEditorViewModel, onDone: () -> Unit) {
    var heading = "Edit profile"
    if (editor.isNew) {
        heading = "Add profile"
    }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(72.dp),
    ) {
        AvatarColumn(editor)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(heading, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            NameField(editor.name, onChange = { name -> editor.name = name })
            ProfileOptions(editor)
            if (editor.error.isNotEmpty()) {
                Text(editor.error, fontSize = 16.sp, color = TvColors.error)
            }
            EditorActions(editor, onDone)
        }
    }
}

@Composable
private fun AvatarColumn(editor: ProfileEditorViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FocusableSurface(
            onClick = { editor.step = EditorStep.AVATAR },
            shape = AVATAR_SHAPE,
            ringWidth = 4.dp,
            modifier = Modifier.size(220.dp),
        ) {
            AsyncImage(
                model = avatarImage(editor.avatarId, editor.name),
                contentDescription = "Profile icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(AVATAR_SHAPE),
            )
        }
        Text("Change icon", fontSize = 16.sp, color = TvColors.secondaryContent)
    }
}

@Composable
private fun EditorActions(editor: ProfileEditorViewModel, onDone: () -> Unit) {
    if (editor.confirmingDelete) {
        DeleteConfirmation(editor, onDone)
        return
    }
    var saveLabel = "Save"
    if (editor.saving) {
        saveLabel = "Saving…"
    }
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PillButton(saveLabel, onClick = { editor.save(onDone) }, primary = true)
        PillButton("Cancel", onClick = onDone)
        if (!editor.isNew) {
            PillButton("Delete profile", onClick = { editor.confirmingDelete = true }, icon = PhosphorIcons.Regular.Trash)
        }
    }
}

@Composable
private fun DeleteConfirmation(editor: ProfileEditorViewModel, onDone: () -> Unit) {
    val cancelButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelButton.requestFocus() }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Delete ${editor.name}? Their history, ratings and list go with it.",
            fontSize = 18.sp,
            modifier = Modifier.width(520.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton("Keep profile", onClick = { editor.confirmingDelete = false }, modifier = Modifier.focusRequester(cancelButton))
            PillButton("Delete", onClick = { editor.delete(onDone) }, icon = PhosphorIcons.Regular.Trash)
        }
    }
}

@Composable
private fun ProfileOptions(editor: ProfileEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow(
            title = "Kids profile",
            description = "Marks this as a child's profile. Titles aren't filtered yet.",
            checked = editor.isChild,
            onToggle = { editor.isChild = !editor.isChild },
        )
        ToggleRow(
            title = "Profile lock",
            description = "Require a 4-digit PIN to open this profile.",
            checked = editor.locked,
            onToggle = { editor.onLockChange(!editor.locked) },
        )
        if (editor.locked) {
            ActionRow("Change PIN", onClick = { editor.step = EditorStep.PIN_CREATE })
        }
        LanguageOptions(editor.languages, onToggle = editor::toggleLanguage)
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
