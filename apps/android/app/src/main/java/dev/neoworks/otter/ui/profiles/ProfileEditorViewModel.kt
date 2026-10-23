package dev.neoworks.otter.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.CreateProfileMutation
import dev.neoworks.otter.graphql.DeleteProfileMutation
import dev.neoworks.otter.graphql.UpdateProfileMutation
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.profiles.DEFAULT_LANGUAGES
import dev.neoworks.otter.profiles.PRESET_AVATARS
import dev.neoworks.otter.profiles.ProfileSession
import kotlinx.coroutines.launch

enum class EditorStep { FORM, AVATAR, PIN_CREATE, PIN_CONFIRM }

/** Add or edit a profile: avatar, name, kids flag and an optional PIN lock. */
class ProfileEditorViewModel(private val existingProfile: ProfileFields?) : ViewModel() {

    val isNew = existingProfile == null

    var name by mutableStateOf(existingProfile?.name.orEmpty())
    var avatarId by mutableStateOf(initialAvatar())
    var isChild by mutableStateOf(existingProfile?.isChild == true)
    var locked by mutableStateOf(existingProfile?.hasPin == true)
        private set
    /** Audio languages, most preferred first. */
    var languages by mutableStateOf(initialLanguages())
        private set
    var step by mutableStateOf(EditorStep.FORM)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var confirmingDelete by mutableStateOf(false)

    // A freshly entered PIN; null keeps the stored one.
    private var newPin: String? = null
    private var pendingPin = ""

    private fun initialAvatar(): String {
        val stored = existingProfile?.avatarUrl
        if (!stored.isNullOrEmpty()) {
            return stored
        }
        return PRESET_AVATARS.random()
    }

    private fun initialLanguages(): List<String> {
        val stored = existingProfile?.languages
        if (stored.isNullOrEmpty()) {
            return DEFAULT_LANGUAGES
        }
        return stored
    }

    /** Picking adds a language at the end of the order; picking again removes it, but one always stays. */
    fun toggleLanguage(code: String) {
        if (!languages.contains(code)) {
            languages = languages + code
            return
        }
        if (languages.size == 1) {
            return
        }
        languages = languages - code
    }

    fun onLockChange(checked: Boolean) {
        if (!checked) {
            locked = false
            newPin = null
            return
        }
        step = EditorStep.PIN_CREATE
    }

    fun onPinCreated(pin: String): String? {
        pendingPin = pin
        step = EditorStep.PIN_CONFIRM
        return null
    }

    fun onPinConfirmed(pin: String): String? {
        if (pin != pendingPin) {
            step = EditorStep.PIN_CREATE
            return "PINs did not match. Try again."
        }
        newPin = pin
        locked = true
        step = EditorStep.FORM
        return null
    }

    fun save(onSaved: () -> Unit) {
        if (name.isBlank()) {
            error = "Enter a name."
            return
        }
        saving = true
        error = ""
        viewModelScope.launch {
            try {
                saveProfile()
                onSaved()
            } catch (failure: Exception) {
                error = failure.message ?: "Could not save the profile."
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val profile = existingProfile ?: return
        viewModelScope.launch {
            try {
                OtterApi.client.mutation(DeleteProfileMutation(profile.id)).execute().dataOrThrow()
                onDeleted()
            } catch (failure: Exception) {
                error = failure.message ?: "Could not delete the profile."
            }
        }
    }

    private suspend fun saveProfile() {
        if (existingProfile == null) {
            createProfile()
            return
        }
        updateProfile(existingProfile)
    }

    private suspend fun createProfile() {
        val mutation = CreateProfileMutation(
            name = name.trim(),
            avatarUrl = Optional.present(avatarId),
            pin = Optional.presentIfNotNull(newPin),
            isChild = Optional.present(isChild),
            languages = Optional.present(languages),
        )
        OtterApi.client.mutation(mutation).execute().dataOrThrow()
    }

    private suspend fun updateProfile(profile: ProfileFields) {
        val mutation = UpdateProfileMutation(
            id = profile.id,
            name = Optional.present(name.trim()),
            avatarUrl = Optional.present(avatarId),
            isChild = Optional.present(isChild),
            pin = pinChange(profile),
            languages = Optional.present(languages),
        )
        val updated = OtterApi.client.mutation(mutation).execute().dataOrThrow().updateProfile
        ProfileSession.refreshIfActive(updated.profileFields)
    }

    // Empty string removes the PIN; absent leaves it untouched.
    private fun pinChange(profile: ProfileFields): Optional<String?> {
        val enteredPin = newPin
        if (enteredPin != null) {
            return Optional.present(enteredPin)
        }
        if (!locked && profile.hasPin) {
            return Optional.present("")
        }
        return Optional.absent()
    }
}
