package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class MemoryHosterStorage : PreferredHosterStorage {
    val hosters = mutableMapOf<String, String>()

    override fun read(seriesKey: String) = hosters[seriesKey]

    override fun write(seriesKey: String, hoster: String) {
        hosters[seriesKey] = hoster
    }
}

private fun link(id: String, label: String?): SourceStream {
    return SourceStream(id, "https://site.example/$id", "plugin", label, null, null, null, null)
}

class PreferredHosterTest {

    private val storage = MemoryHosterStorage()
    private val preferredHoster = PreferredHoster(storage)

    private fun playEpisode(mediaId: String, linkHoster: String?, seconds: Double) {
        preferredHoster.open("profile:1", mediaId)
        preferredHoster.confirmSeries("series:1")
        preferredHoster.sourceAttached(linkHoster)
        var position = 0.0
        while (position <= seconds) {
            preferredHoster.follow(position, 2400.0)
            position += 5.0
        }
    }

    @Test
    fun theHosterIsTakenFromTheLabel() {
        assertEquals("voe", hosterOf(link("1", "VOE — Deutsch")))
        assertEquals("doodstream", hosterOf(link("2", " Doodstream ")))
        assertNull(hosterOf(link("3", null)))
        assertNull(hosterOf(link("4", " — Deutsch")))
    }

    @Test
    fun theHostersLinksComeFirstAndTheOthersFollowInOrder() {
        val links = listOf(link("1", "Streamtape — de"), link("2", "VOE — de"), link("3", "Filemoon — de"), link("4", "voe — en"))

        val groups = preferredFirst(links, "voe")

        assertEquals(listOf(listOf("2", "4"), listOf("1", "3")), groups.map { group -> group.map { stream -> stream.id } })
    }

    @Test
    fun withoutAPreferenceOrALinkOfItTheGroupStaysWhole() {
        val links = listOf(link("1", "Streamtape — de"), link("2", "Filemoon — de"))

        assertEquals(listOf(links), preferredFirst(links, null))
        assertEquals(listOf(links), preferredFirst(links, "voe"))
        assertEquals(emptyList<List<SourceStream>>(), preferredFirst(emptyList(), "voe").filter { group -> group.isNotEmpty() })
    }

    @Test
    fun theNextEpisodeStartsWithTheHosterThatPlayedTheLastOne() {
        playEpisode("episode:1", "voe", seconds = 600.0)
        preferredHoster.learnEpisodes("series:1", listOf("episode:1", "episode:2"))

        preferredHoster.open("profile:1", "episode:2")

        assertEquals("voe", preferredHoster.hoster)
    }

    @Test
    fun aLinkMustPlayForAWhileWithoutAnErrorFirst() {
        playEpisode("episode:1", "voe", seconds = 120.0)
        assertNull(storage.hosters["profile:1|series:1"])

        preferredHoster.open("profile:1", "episode:2")
        preferredHoster.confirmSeries("series:1")
        preferredHoster.sourceAttached("filemoon")
        preferredHoster.follow(0.0, 2400.0)
        preferredHoster.sourceFailed()
        preferredHoster.follow(900.0, 2400.0)

        assertNull(storage.hosters["profile:1|series:1"])
    }

    @Test
    fun theLinkThatTookOverAfterAFailureEarnsTheMemory() {
        preferredHoster.open("profile:1", "episode:1")
        preferredHoster.confirmSeries("series:1")
        preferredHoster.sourceAttached("filemoon")
        preferredHoster.follow(0.0, 2400.0)
        preferredHoster.sourceFailed()
        preferredHoster.sourceAttached("voe")
        preferredHoster.follow(100.0, 2400.0)
        preferredHoster.follow(500.0, 2400.0)

        assertEquals("voe", storage.hosters["profile:1|series:1"])
    }

    @Test
    fun theMemoryBelongsToTheSeriesAndTheProfile() {
        playEpisode("episode:1", "voe", seconds = 600.0)

        preferredHoster.open("profile:2", "episode:1")
        preferredHoster.confirmSeries("series:1")
        assertNull(preferredHoster.hoster)

        preferredHoster.open("profile:1", "episode:9")
        preferredHoster.confirmSeries("series:2")
        assertNull(preferredHoster.hoster)
    }

    @Test
    fun aMovieHasNoMemory() {
        preferredHoster.open("profile:1", "movie:1")
        preferredHoster.confirmSeries(null)
        preferredHoster.sourceAttached("voe")
        preferredHoster.follow(0.0, 6000.0)
        preferredHoster.follow(3000.0, 6000.0)

        assertEquals(emptyMap<String, String>(), storage.hosters)
    }
}
