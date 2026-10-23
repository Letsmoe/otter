package dev.neoworks.otter.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.PersonCoStarsQuery
import dev.neoworks.otter.graphql.PersonDetailQuery
import dev.neoworks.otter.graphql.PersonRecommendedQuery
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.toRailItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

data class PersonDetail(
    val name: String,
    val photoUrl: String?,
    val lifespan: String?,
    val placeOfBirth: String?,
    val knownForDepartment: String?,
    val biography: String?,
    val filmography: List<RailItem>,
)

private const val CO_STAR_LIMIT = 15

class PersonViewModel(private val personId: String) : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<PersonDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<PersonDetail>> = mutableState

    // Loaded on its own: the first visit may fetch credits for several titles.
    private val mutableCoStars = MutableStateFlow<List<ActorItem>>(emptyList())
    val coStars: StateFlow<List<ActorItem>> = mutableCoStars

    private val mutableRecommended = MutableStateFlow<List<RailItem>>(emptyList())
    val recommended: StateFlow<List<RailItem>> = mutableRecommended

    init {
        load()
    }

    fun load() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                mutableState.value = fetchPerson()
            } catch (error: Exception) {
                mutableState.value = LoadState.Failed(error.message.orEmpty())
            }
        }
        viewModelScope.launch { loadCoStars() }
        viewModelScope.launch { loadRecommended() }
    }

    private suspend fun loadRecommended() {
        OtterApi.awaitBackend()
        val profileId = ProfileSession.activeProfile.value?.id.orEmpty()
        try {
            val person = OtterApi.client.query(PersonRecommendedQuery(personId, profileId)).execute().dataOrThrow().person
            if (person == null) {
                return
            }
            mutableRecommended.value = person.recommendedFor.map { title -> title.titleCard.toRailItem() }
        } catch (error: Exception) {
            mutableRecommended.value = emptyList()
        }
    }

    private suspend fun loadCoStars() {
        OtterApi.awaitBackend()
        try {
            val person = OtterApi.client.query(PersonCoStarsQuery(personId, CO_STAR_LIMIT)).execute().dataOrThrow().person
            if (person == null) {
                return
            }
            mutableCoStars.value = person.coStars.map { coStar ->
                ActorItem(coStar.person.id, coStar.person.name, tmdbImageUrl(coStar.person.profilePath, "w185"), sharedLabel(coStar.sharedTitles))
            }
        } catch (error: Exception) {
            mutableCoStars.value = emptyList()
        }
    }

    private fun sharedLabel(sharedTitles: Int): String {
        if (sharedTitles == 1) {
            return "1 title together"
        }
        return "$sharedTitles titles together"
    }

    private suspend fun fetchPerson(): LoadState<PersonDetail> {
        val data = OtterApi.client.query(PersonDetailQuery(personId)).execute().dataOrThrow()
        val person = data.person
        if (person == null) {
            return LoadState.Failed("Person not found")
        }
        return LoadState.Loaded(
            PersonDetail(
                name = person.name,
                photoUrl = tmdbImageUrl(person.profilePath, "h632"),
                lifespan = lifespanOf(person.birthday, person.deathday),
                placeOfBirth = person.placeOfBirth,
                knownForDepartment = person.knownForDepartment,
                biography = person.biography,
                filmography = person.filmography.map { title -> title.titleCard.toRailItem() },
            ),
        )
    }

    private fun lifespanOf(birthday: String?, deathday: String?): String? {
        if (birthday == null) {
            return null
        }
        val birthYear = birthday.take(4)
        if (deathday == null) {
            return "Born $birthYear (${ageInYears(birthday, LocalDate.now())})"
        }
        return "$birthYear – ${deathday.take(4)}"
    }

    private fun ageInYears(birthday: String, until: LocalDate): Int {
        try {
            return Period.between(LocalDate.parse(birthday), until).years
        } catch (error: Exception) {
            return 0
        }
    }
}
