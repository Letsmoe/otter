package dev.neoworks.otter.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.api.posterUrl
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.ProfilesQuery
import dev.neoworks.otter.graphql.SpotlightTitleQuery
import dev.neoworks.otter.graphql.TrendingQuery
import dev.neoworks.otter.graphql.VerifyPinMutation
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.profile.ProfileFlight
import dev.neoworks.otter.ui.LoadState
import kotlinx.coroutines.launch

sealed interface GateScreen {
    data object Pick : GateScreen
    data class Unlock(val profile: ProfileFields, val thenEdit: Boolean) : GateScreen
    data class Edit(val profile: ProfileFields) : GateScreen
    data object Add : GateScreen
}

/** A trending title shown behind the profile picker. */
data class Spotlight(val title: String, val posterUrl: String, val logoUrl: String?, val tagline: String?)

private const val SPOTLIGHT_CANDIDATES = 8

class ProfileGateViewModel : ViewModel() {

    var profiles by mutableStateOf<LoadState<List<ProfileFields>>>(LoadState.Loading)
        private set
    var spotlight by mutableStateOf<Spotlight?>(null)
        private set
    var screen by mutableStateOf<GateScreen>(GateScreen.Pick)
        private set
    var managing by mutableStateOf(false)

    init {
        loadProfiles()
        viewModelScope.launch { spotlight = fetchSpotlight() }
    }

    fun loadProfiles() {
        profiles = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val data = OtterApi.client.query(ProfilesQuery()).execute().dataOrThrow()
                profiles = LoadState.Loaded(data.profiles.map { profile -> profile.profileFields })
            } catch (error: Exception) {
                profiles = LoadState.Failed("Can't reach the Otter server.")
            }
        }
    }

    fun backToPicker() {
        screen = GateScreen.Pick
        loadProfiles()
    }

    fun openProfile(profile: ProfileFields) {
        if (managing) {
            editProfile(profile)
            return
        }
        if (profile.hasPin) {
            screen = GateScreen.Unlock(profile, thenEdit = false)
            return
        }
        ProfileSession.login(profile)
    }

    fun openAdd() {
        screen = GateScreen.Add
    }

    // Locked profiles need their PIN before they can be changed.
    private fun editProfile(profile: ProfileFields) {
        if (profile.hasPin) {
            screen = GateScreen.Unlock(profile, thenEdit = true)
            return
        }
        screen = GateScreen.Edit(profile)
    }

    /** Returns an error message, or null when the PIN opened the profile. */
    suspend fun verifyPin(unlock: GateScreen.Unlock, pin: String): String? {
        val result = try {
            OtterApi.client.mutation(VerifyPinMutation(unlock.profile.id, pin)).execute().dataOrThrow().verifyPin
        } catch (error: Exception) {
            return error.message ?: "Could not check the PIN."
        }
        if (!result.success) {
            return waitMessage(result.waitSeconds)
        }
        if (unlock.thenEdit) {
            screen = GateScreen.Edit(unlock.profile)
            return null
        }
        ProfileFlight.start(avatarImage(unlock.profile.avatarUrl, unlock.profile.name), from = null)
        ProfileSession.login(unlock.profile)
        return null
    }

    private fun waitMessage(waitSeconds: Int): String {
        if (waitSeconds > 0) {
            return "Too many attempts. Try again in ${waitSeconds}s."
        }
        return "Incorrect PIN. Try again."
    }

    /** One of the top trending titles with poster, logo and tagline; null offline. */
    private suspend fun fetchSpotlight(): Spotlight? {
        OtterApi.awaitBackend()
        val trending = try {
            OtterApi.client.query(TrendingQuery(Optional.absent())).execute().dataOrThrow().trending
        } catch (error: Exception) {
            return null
        }
        val candidates = trending.map { item -> item.titleCard }.filter { card -> card.posterPath != null }.take(SPOTLIGHT_CANDIDATES)
        val picked = candidates.randomOrNull() ?: return null
        val fallback = Spotlight(picked.title, posterUrl(picked.posterPath).orEmpty(), logoUrl = null, tagline = null)
        val detail = try {
            OtterApi.client.query(SpotlightTitleQuery(picked.id)).execute().dataOrThrow().title
        } catch (error: Exception) {
            return fallback
        }
        if (detail == null) {
            return fallback
        }
        return Spotlight(
            title = picked.title,
            posterUrl = tmdbImageUrl(detail.posterPath, "original") ?: fallback.posterUrl,
            logoUrl = tmdbImageUrl(detail.logoPath, "w500"),
            tagline = detail.tagline?.takeIf { tagline -> tagline.isNotBlank() },
        )
    }
}
