package dev.neoworks.otter.profiles

import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.AppScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.UpdateProfileMutation
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.graphql.type.SubtitleSize
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Caption and audio preferences of a profile; a null field stays as it is. */
data class PreferenceChange(
    val subtitlesEnabled: Boolean? = null,
    val subtitleLanguage: String? = null,
    val subtitleSize: SubtitleSize? = null,
    val voiceBoost: Boolean? = null,
)

/** The language captions start in: the profile's pick, else its first audio language. */
fun ProfileFields.captionLanguage(): String {
    val picked = subtitleLanguage
    if (!picked.isNullOrEmpty()) {
        return picked
    }
    return languages.firstOrNull().orEmpty()
}

private fun ProfileFields.withChange(change: PreferenceChange): ProfileFields {
    return copy(
        subtitlesEnabled = change.subtitlesEnabled ?: subtitlesEnabled,
        subtitleLanguage = change.subtitleLanguage ?: subtitleLanguage,
        subtitleSize = change.subtitleSize ?: subtitleSize,
        voiceBoost = change.voiceBoost ?: voiceBoost,
    )
}

/** Saves caption and voice boost choices on the active profile, applying them at once. */
object ProfilePreferences {

    /** Shows the change right away and saves it in the background; a failed save restores the old profile. */
    fun change(change: PreferenceChange) {
        val before = ProfileSession.activeProfile.value ?: return
        ProfileSession.refreshIfActive(before.withChange(change))
        AppScope.launch { save(before, change) }
    }

    private suspend fun save(before: ProfileFields, change: PreferenceChange) {
        try {
            val mutation = UpdateProfileMutation(
                id = before.id,
                subtitlesEnabled = Optional.presentIfNotNull(change.subtitlesEnabled),
                subtitleLanguage = Optional.presentIfNotNull(change.subtitleLanguage),
                subtitleSize = Optional.presentIfNotNull(change.subtitleSize),
                voiceBoost = Optional.presentIfNotNull(change.voiceBoost),
            )
            val saved = OtterApi.client.mutation(mutation).execute().dataOrThrow().updateProfile
            ProfileSession.refreshIfActive(saved.profileFields)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ProfileSession.refreshIfActive(before)
            Toasts.push(error.message ?: "Could not save", ToastKind.ERROR)
        }
    }
}
