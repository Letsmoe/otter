package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLinkSearch : LinkSearch {
    var startedFor: String? = null
    var stopped = false
    var link: PrefetchedLink? = null

    override fun start(mediaId: String) {
        startedFor = mediaId
    }

    override fun resolved() = link

    override fun stop() {
        stopped = true
    }
}

class NextEpisodePrefetchTest {

    private val searches = mutableListOf<FakeLinkSearch>()
    private val prefetch = NextEpisodePrefetch { FakeLinkSearch().also { search -> searches.add(search) } }
    private val link = PrefetchedLink(PlayingSource("http://localhost/video", "video/mp4", "stream:1"), null)

    @Test
    fun theNextEpisodeIsLookedUpOnce() {
        prefetch.start("episode:2")
        prefetch.start("episode:2")

        assertEquals(1, searches.size)
        assertEquals("episode:2", searches.single().startedFor)
    }

    @Test
    fun theResolvedLinkIsHandedToTheEpisodeItWasFoundFor() {
        prefetch.start("episode:2")
        searches.single().link = link

        assertSame(link, prefetch.take("episode:2"))
        assertTrue(searches.single().stopped)
    }

    @Test
    fun aSearchThatIsNotDoneYetHandsOverNothingAndEnds() {
        prefetch.start("episode:2")

        assertNull(prefetch.take("episode:2"))
        assertTrue(searches.single().stopped)
    }

    @Test
    fun anotherEpisodeGetsNoLinkAndEndsTheSearch() {
        prefetch.start("episode:2")
        searches.single().link = link

        assertNull(prefetch.take("episode:7"))
        assertTrue(searches.single().stopped)
        assertNull(prefetch.take("episode:2"))
    }

    @Test
    fun aLinkIsHandedOverOnlyOnce() {
        prefetch.start("episode:2")
        searches.single().link = link
        prefetch.take("episode:2")

        assertNull(prefetch.take("episode:2"))
    }

    @Test
    fun lookingUpAnotherEpisodeEndsTheEarlierSearch() {
        prefetch.start("episode:2")
        prefetch.start("episode:3")

        assertEquals(2, searches.size)
        assertTrue(searches.first().stopped)
        assertEquals("episode:3", searches.last().startedFor)
    }
}
