package dev.neoworks.otter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.graphql.type.SubtitleSize
import dev.neoworks.otter.profiles.LANGUAGE_CHOICES
import dev.neoworks.otter.profiles.PreferenceChange
import dev.neoworks.otter.profiles.ProfilePreferences
import dev.neoworks.otter.profiles.captionLanguage
import dev.neoworks.otter.ui.components.FilterSelect

private val captionSizes = listOf(
    SubtitleSize.SMALL to "Small",
    SubtitleSize.MEDIUM to "Medium",
    SubtitleSize.LARGE to "Large",
)

/** Caption and voice boost rows; every change is saved on the active profile. */
@Composable
fun CaptionSettingsRows(profile: ProfileFields) {
    SettingsSwitchRow("Captions", "Show subtitles when a title starts", profile.subtitlesEnabled) { enabled ->
        ProfilePreferences.change(PreferenceChange(subtitlesEnabled = enabled))
    }
    SettingsDivider()
    SettingsChoiceRow("Caption language", captionLanguageOptions(profile), profile.captionLanguage()) { language ->
        ProfilePreferences.change(PreferenceChange(subtitleLanguage = language))
    }
    SettingsDivider()
    SettingsChoiceRow("Caption size", captionSizes, normalizedSize(profile.subtitleSize)) { size ->
        ProfilePreferences.change(PreferenceChange(subtitleSize = size))
    }
    SettingsDivider()
    SettingsSwitchRow("Voice boost", "Louder dialogue, softer music and effects", profile.voiceBoost) { enabled ->
        ProfilePreferences.change(PreferenceChange(voiceBoost = enabled))
    }
}

@Composable
private fun <Value> SettingsChoiceRow(title: String, options: List<Pair<Value, String>>, selected: Value, onSelect: (Value) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
        }
        FilterSelect(title, options, selected, onSelect, showLabel = false)
    }
}

// The profile's own languages first, then the rest of the common ones.
private fun captionLanguageOptions(profile: ProfileFields): List<Pair<String, String>> {
    val choices = LANGUAGE_CHOICES.sortedBy { choice -> languagePosition(profile, choice.code) }
    val options = choices.map { choice -> choice.code to choice.name }
    val current = profile.captionLanguage()
    if (options.any { option -> option.first == current }) {
        return options
    }
    return listOf(current to current) + options
}

private fun languagePosition(profile: ProfileFields, code: String): Int {
    val position = profile.languages.indexOf(code)
    if (position == -1) {
        return profile.languages.size
    }
    return position
}

private fun normalizedSize(size: SubtitleSize): SubtitleSize {
    if (size == SubtitleSize.UNKNOWN__) {
        return SubtitleSize.MEDIUM
    }
    return size
}
