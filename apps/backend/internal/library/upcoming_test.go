package library

import (
	"reflect"
	"testing"
	"time"
)

func day(value string) time.Time {
	parsed, err := time.Parse(airDateLayout, value)
	if err != nil {
		panic(err)
	}
	return parsed
}

func episodeAiring(seasonNumber, episodeNumber int, airDate string) Episode {
	return Episode{TMDBID: seasonNumber*100 + episodeNumber, SeasonNumber: seasonNumber, EpisodeNumber: episodeNumber, AirDate: airDate}
}

func TestUpcomingKeepsEpisodesFromTodayUntilTheLastDay(t *testing.T) {
	series := Title{MediaType: MediaTypeSeries, TMDBID: 1, Title: "Severance"}
	episodes := []Episode{
		episodeAiring(2, 1, "2026-11-20"),
		episodeAiring(2, 2, "2026-11-21"),
		episodeAiring(2, 3, "2026-12-19"),
		episodeAiring(2, 4, "2026-12-20"),
	}

	upcoming := upcomingWithin(series, episodes, day("2026-11-21"), 28)

	if len(upcoming) != 2 || upcoming[0].Episode.EpisodeNumber != 2 || upcoming[1].Episode.EpisodeNumber != 3 {
		t.Fatalf("unexpected upcoming episodes: %+v", upcoming)
	}
}

func TestUpcomingSkipsSpecialsAndEpisodesWithoutADate(t *testing.T) {
	series := Title{MediaType: MediaTypeSeries, TMDBID: 1}
	episodes := []Episode{
		episodeAiring(0, 1, "2026-11-22"),
		episodeAiring(1, 1, ""),
		episodeAiring(1, 2, "someday"),
		episodeAiring(1, 3, "2026-11-22"),
	}

	upcoming := upcomingWithin(series, episodes, day("2026-11-21"), 28)

	if len(upcoming) != 1 || upcoming[0].Episode.EpisodeNumber != 3 {
		t.Fatalf("unexpected upcoming episodes: %+v", upcoming)
	}
}

func TestUpcomingIsSortedByDayThenSeriesTitle(t *testing.T) {
	zebra := Title{TMDBID: 1, Title: "Zebra"}
	apple := Title{TMDBID: 2, Title: "Apple"}
	upcoming := []UpcomingEpisode{
		{Series: zebra, Episode: episodeAiring(1, 1, "2026-11-23")},
		{Series: zebra, Episode: episodeAiring(1, 2, "2026-11-22")},
		{Series: apple, Episode: episodeAiring(1, 5, "2026-11-23")},
	}

	sortUpcoming(upcoming)

	order := []string{upcoming[0].Series.Title, upcoming[1].Series.Title, upcoming[2].Series.Title}
	if !reflect.DeepEqual(order, []string{"Zebra", "Apple", "Zebra"}) || upcoming[0].Episode.EpisodeNumber != 2 {
		t.Fatalf("unexpected order: %v", order)
	}
}

func TestNewestRegularSeasonsSkipSpecialsAndOlderSeasons(t *testing.T) {
	seasons := []Season{{SeasonNumber: 1}, {SeasonNumber: 2}, {SeasonNumber: 3}, {SeasonNumber: 0}}

	numbers := NewestRegularSeasonNumbers(seasons, 2)

	if !reflect.DeepEqual(numbers, []int{2, 3}) {
		t.Fatalf("seasons = %v", numbers)
	}
}

func TestEndedSeriesHaveNoUpcomingEpisodes(t *testing.T) {
	if !hasEnded(Title{Status: "Ended"}) || !hasEnded(Title{Status: "Canceled"}) {
		t.Fatal("ended and canceled series should count as ended")
	}
	if hasEnded(Title{Status: "Returning Series"}) || hasEnded(Title{}) {
		t.Fatal("running or unknown series should not count as ended")
	}
}
