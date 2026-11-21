package com.letsmoe.otter.ui.upcoming

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class UpcomingDaysTest {

    // A Saturday.
    private val today = LocalDate.of(2026, 11, 21)

    private fun episode(title: String, daysFromToday: Long): UpcomingEpisode {
        val item = RailItem("series:1", MediaType.SERIES, title, 2022, null)
        return UpcomingEpisode(item, today.plusDays(daysFromToday), null)
    }

    private fun group(episodes: List<UpcomingEpisode>) = groupByDay(episodes, today, Locale.ENGLISH)

    @Test
    fun todayAndTomorrowAreNamedAndLaterDaysUseTheWeekday() {
        assertEquals("Today", dayHeading(today, today, Locale.ENGLISH))
        assertEquals("Tomorrow", dayHeading(today.plusDays(1), today, Locale.ENGLISH))
        assertEquals("Tuesday", dayHeading(today.plusDays(3), today, Locale.ENGLISH))
    }

    @Test
    fun episodesOfOneDayShareASectionWithItsDate() {
        val days = group(listOf(episode("Severance", 1), episode("Bosch", 1), episode("Andor", 3)))

        assertEquals(listOf("Tomorrow", "Tuesday"), days.map { day -> day.heading })
        assertEquals(listOf("22 Nov", "24 Nov"), days.map { day -> day.dateLabel })
        assertEquals(listOf("Severance", "Bosch"), days[0].episodes.map { entry -> entry.item.title })
    }

    @Test
    fun daysComeSoonestFirstWhateverTheInputOrder() {
        val days = group(listOf(episode("Andor", 9), episode("Severance", 0), episode("Bosch", 2)))

        assertEquals(listOf("21 Nov", "23 Nov", "30 Nov"), days.map { day -> day.dateLabel })
        assertEquals(listOf("Severance", "Bosch", "Andor"), days.flatMap { day -> day.episodes }.map { entry -> entry.item.title })
    }

    @Test
    fun episodesThatAlreadyAiredAreLeftOut() {
        val days = group(listOf(episode("Severance", -1), episode("Bosch", 0)))

        assertEquals(1, days.size)
        assertEquals("Bosch", days[0].episodes[0].item.title)
    }

    @Test
    fun nothingAiringGivesNoDays() {
        assertTrue(group(emptyList()).isEmpty())
    }
}
