package dev.neoworks.otter.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.profiles.LANGUAGE_CHOICES
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.components.GlassColor
import dev.neoworks.otter.ui.components.OtterSwitch
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.SubtleFill

private const val NAME_MAX_LENGTH = 32

/** [profile] is null when adding a new one. */
@Composable
fun ProfileEditor(profile: ProfileFields?, onBack: () -> Unit, onSaved: () -> Unit) {
    val editor = viewModel(key = "profile-editor-${profile?.id}") { ProfileEditorViewModel(profile) }
    when (editor.step) {
        EditorStep.AVATAR -> AvatarChooser(
            selected = editor.avatarId,
            onBack = { editor.step = EditorStep.FORM },
            onSelect = { chosen ->
                editor.avatarId = chosen
                editor.step = EditorStep.FORM
            },
        )
        EditorStep.PIN_CREATE -> PinPad(
            title = "Profile Lock",
            message = "Create a 4-digit PIN for this profile.",
            onBack = { editor.step = EditorStep.FORM },
            onComplete = { pin -> editor.onPinCreated(pin) },
        )
        EditorStep.PIN_CONFIRM -> PinPad(
            title = "Profile Lock",
            message = "Enter the PIN again to confirm.",
            onBack = { editor.step = EditorStep.PIN_CREATE },
            onComplete = { pin -> editor.onPinConfirmed(pin) },
        )
        EditorStep.FORM -> EditorForm(editor, onBack, onSaved)
    }
}

@Composable
private fun EditorForm(editor: ProfileEditorViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    var title = "Edit Profile"
    if (editor.isNew) {
        title = "Add Profile"
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AvatarButton(avatarImage(editor.avatarId, editor.name), onClick = { editor.step = EditorStep.AVATAR })
            NameField(editor.name, onChange = { value -> editor.name = value.take(NAME_MAX_LENGTH) })
            if (editor.error.isNotEmpty()) {
                Text(
                    editor.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ProfileOptions(editor)
            SaveButton(editor, onSaved)
            if (!editor.isNew) {
                DeleteSection(editor, onDeleted = onSaved)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AvatarButton(avatarModel: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(112.dp).clickable(onClick = onClick)) {
            AsyncImage(avatarModel, contentDescription = "Change icon", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(8.dp, 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 20.dp),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.Center) {
                    if (name.isEmpty()) {
                        Text("Name", color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun ProfileOptions(editor: ProfileEditorViewModel) {
    Column(Modifier.clip(RoundedCornerShape(24.dp)).background(SubtleFill).padding(horizontal = 16.dp)) {
        OptionRow(Icons.Filled.Face, "Kids Profile", "Marks this as a child's profile. Titles aren't filtered yet.") {
            OtterSwitch(editor.isChild, onCheckedChange = { value -> editor.isChild = value })
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        OptionRow(Icons.Filled.Lock, "Profile Lock", "Require a 4-digit PIN to open this profile.") {
            OtterSwitch(editor.locked, onCheckedChange = editor::onLockChange)
        }
        if (editor.locked) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            ChangePinRow(onClick = { editor.step = EditorStep.PIN_CREATE })
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        LanguageOptions(editor.languages, onToggle = editor::toggleLanguage)
    }
}

@Composable
private fun LanguageOptions(languages: List<String>, onToggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Languages", fontWeight = FontWeight.SemiBold)
        Text(
            "Sources in these languages are tried first, in this order. Tap to add or remove.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.6f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in LANGUAGE_CHOICES) {
                LanguageChip(choice.name, languages.indexOf(choice.code), onClick = { onToggle(choice.code) })
            }
        }
    }
}

/** [position] is the place in the profile's order, -1 when not picked. */
@Composable
private fun LanguageChip(name: String, position: Int, onClick: () -> Unit) {
    val picked = position >= 0
    var background = Color.White.copy(alpha = 0.08f)
    var label = name
    var textAlpha = 0.7f
    if (picked) {
        background = Color.White.copy(alpha = 0.22f)
        label = "${position + 1}. $name"
        textAlpha = 1f
    }
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(textAlpha),
    )
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, description: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.alpha(0.7f))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        control()
    }
}

@Composable
private fun ChangePinRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Change PIN",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 40.dp),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.alpha(0.6f))
    }
}

@Composable
private fun SaveButton(editor: ProfileEditorViewModel, onSaved: () -> Unit) {
    var label = "Save"
    if (editor.isNew) {
        label = "Create Profile"
    }
    Button(
        onClick = { editor.save(onSaved) },
        enabled = !editor.saving && editor.name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        if (editor.saving) {
            CircularProgressIndicator(Modifier.size(14.dp).padding(end = 4.dp), color = Color.Black, strokeWidth = 2.dp)
        }
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteSection(editor: ProfileEditorViewModel, onDeleted: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        if (!editor.confirmingDelete) {
            TextButton(onClick = { editor.confirmingDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                Text("Delete Profile", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
            return@Box
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Delete this profile and its history?", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { editor.confirmingDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                ) { Text("Cancel") }
                Button(
                    onClick = { editor.delete(onDeleted) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                ) { Text("Delete") }
            }
        }
    }
}
