package com.letsmoe.otter.ui.player

import com.letsmoe.otter.settings.AutoSkipIntro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MILLISECONDS = 24 * 60 * 60 * 1000L

private class MemoryStorage : SkipHabitStorage {
    val intros = mutableMapOf<String, IntroHabit>()
    val finished = mutableMapOf<String, FinishedEpisode>()

    override fun readIntroHabit(seriesKey: String) = intros[seriesKey] ?: IntroHabit()

    override fun writeIntroHabit(seriesKey: String, habit: IntroHabit) {
        intros[seriesKey] = habit
    }

    override fun readFinishedEpisode(seriesKey: String) = finished[seriesKey]

    override fun writeFinishedEpisode(seriesKey: String, episode: FinishedEpisode) {
        finished[seriesKey] = episode
    }
}

class SkipHabitsTest {

    private val storage = MemoryStorage()
    private var nowMillis = 10 * DAY_MILLISECONDS
    private val habits = SkipHabits(storage) { nowMillis }
    private val intro = SkipJump("intro", 10.0, 70.0)
    private val recap = SkipJump("recap", 0.0, 10.0)

    private fun skipByHand(episodeId: String) {
        habits.open("profile:1", "series:1", episodeId, null)
        habits.skippedByHand(intro)
    }

    private fun watchThrough(episodeId: String) {
        habits.open("profile:1", "series:1", episodeId, null)
        var seconds = 60.0
        while (seconds < 72.0) {
            habits.follow(listOf(intro), seconds, playing = true, inCredits = false)
            seconds += 0.25
        }
    }

    @Test
    fun anIntroSkippedByHandThreeTimesInARowIsSkippedOnItsOwn() {
        skipByHand("episode:1")
        skipByHand("episode:2")
        habits.open("profile:1", "series:1", "episode:3", null)
        assertFalse(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))

        habits.skippedByHand(intro)
        habits.open("profile:1", "series:1", "episode:4", null)

        assertTrue(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
        assertTrue(habits.skips(intro, AutoSkipIntro.HABITS, casting = true))
    }

    @Test
    fun theHabitBelongsToTheSeries() {
        repeat(3) { index -> skipByHand("episode:$index") }

        habits.open("profile:1", "series:2", "episode:9", null)
        assertFalse(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
        habits.open("profile:2", "series:1", "episode:9", null)
        assertFalse(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun anIntroWatchedThroughTwiceIsKeptAgain() {
        repeat(3) { index -> skipByHand("episode:$index") }

        watchThrough("episode:4")
        habits.open("profile:1", "series:1", "episode:5", null)
        assertTrue(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))

        watchThrough("episode:5")
        habits.open("profile:1", "series:1", "episode:6", null)
        assertFalse(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun aSkipBetweenWatchesStartsTheCountAgain() {
        repeat(3) { index -> skipByHand("episode:$index") }
        watchThrough("episode:4")
        skipByHand("episode:5")
        watchThrough("episode:6")

        habits.open("profile:1", "series:1", "episode:7", null)
        assertTrue(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun recapsAreSkippedWhenThePreviousEpisodeFinishedLessThanADayAgo() {
        habits.open("profile:1", "series:1", "episode:1", null)
        habits.episodeEnded()

        nowMillis += DAY_MILLISECONDS - 1
        habits.open("profile:1", "series:1", "episode:2", "episode:1")
        assertTrue(habits.skips(recap, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun recapsStayAfterABreakOrWhenTheEpisodeBeforeWasNotWatched() {
        habits.open("profile:1", "series:1", "episode:1", null)
        habits.follow(emptyList(), 2500.0, playing = true, inCredits = true)

        nowMillis += DAY_MILLISECONDS
        habits.open("profile:1", "series:1", "episode:2", "episode:1")
        assertFalse(habits.skips(recap, AutoSkipIntro.HABITS, casting = false))

        habits.open("profile:1", "series:1", "episode:5", "episode:4")
        assertFalse(habits.skips(recap, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun theOtherModesIgnoreTheHabits() {
        assertTrue(habits.skips(intro, AutoSkipIntro.ALWAYS, casting = false))
        assertFalse(habits.skips(intro, AutoSkipIntro.OFF, casting = true))
        assertFalse(habits.skips(intro, AutoSkipIntro.TELEVISION, casting = false))
        assertTrue(habits.skips(intro, AutoSkipIntro.TELEVISION, casting = true))
    }

    @Test
    fun anAutomaticSkipOrASeekOverTheIntroSaysNothing() {
        habits.open("profile:1", "series:1", "episode:1", null)
        habits.follow(listOf(intro), 12.0, playing = true, inCredits = false)
        habits.follow(listOf(intro), 70.0, playing = true, inCredits = false)
        habits.follow(listOf(intro), 200.0, playing = true, inCredits = false)

        assertEquals(IntroHabit(), storage.intros["profile:1|series:1"] ?: IntroHabit())
    }

    @Test
    fun aMovieHasNoHabits() {
        habits.open("profile:1", null, "movie:1", null)
        habits.skippedByHand(intro)
        habits.episodeEnded()

        assertTrue(storage.intros.isEmpty())
        assertTrue(storage.finished.isEmpty())
        assertFalse(habits.skips(intro, AutoSkipIntro.HABITS, casting = false))
    }

    @Test
    fun habitsSurviveBeingWrittenDown() {
        val habit = IntroHabit(2, 0, true)
        assertEquals(habit, IntroHabit.decode(habit.encode()))
        assertEquals(IntroHabit(), IntroHabit.decode("nonsense"))
        val finished = FinishedEpisode("episode:1,2", 5L)
        assertEquals(finished, FinishedEpisode.decode(finished.encode()))
        assertNull(FinishedEpisode.decode(null))
    }

    @Test
    fun theEpisodeBeforeFollowsTheSeriesOrder() {
        val episodes = listOf(
            PlayerEpisode("e1", 1, 1, null, null, null, null),
            PlayerEpisode("e2", 1, 2, null, null, null, null),
        )

        assertNull(episodes.episodeBefore("e1"))
        assertEquals("e1", episodes.episodeBefore("e2")?.id)
        assertNull(episodes.episodeBefore("missing"))
    }
}
