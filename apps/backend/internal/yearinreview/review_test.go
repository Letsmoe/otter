package yearinreview

import (
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/library"
)

var utc = time.UTC

func at(month time.Month, day, hour int) time.Time {
	return time.Date(2026, month, day, hour, 0, 0, 0, utc)
}

func movie(id int, startedAt, updatedAt time.Time, seconds float64, completed bool) Activity {
	return Activity{
		Media:           library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: id},
		ProgressSeconds: seconds, DurationSeconds: 7200, Completed: completed,
		StartedAt: startedAt, UpdatedAt: updatedAt,
	}
}

func episode(id, seriesID int, startedAt, updatedAt time.Time, finished bool) Activity {
	return Activity{
		Media:    library.MediaRef{MediaType: library.MediaTypeEpisode, MediaID: id},
		SeriesID: seriesID, ProgressSeconds: 2400, DurationSeconds: 2700, Completed: finished,
		StartedAt: startedAt, UpdatedAt: updatedAt,
	}
}

func seriesRefOf(id int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: id}
}

func TestOnlyTheYearsActivityCounts(t *testing.T) {
	activities := []Activity{
		movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 7200, true),
		movie(2, at(time.December, 31, 20), time.Date(2025, time.December, 31, 22, 0, 0, 0, utc), 7200, true),
		movie(3, at(time.June, 1, 20), at(time.June, 1, 20), 30, false),
	}
	review := Build(activities, nil, 2026, utc)
	if review.WatchedSeconds != 7200 || review.MoviesFinished != 1 {
		t.Fatalf("review = %+v, want only the March film (the others are last year's or barely opened)", review)
	}
}

func TestEmptyYearHasNoActivity(t *testing.T) {
	review := Build(nil, nil, 2026, utc)
	if review.HasActivity || review.TopSeries != nil || review.First != nil {
		t.Fatalf("review = %+v", review)
	}
}

func TestWatchedTimeIsCappedAtTheRuntime(t *testing.T) {
	activity := movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 9000, true)
	review := Build([]Activity{activity}, nil, 2026, utc)
	if review.WatchedSeconds != 7200 {
		t.Fatalf("watched = %v, want the runtime", review.WatchedSeconds)
	}
}

func TestFinishedCountsSplitMoviesAndEpisodes(t *testing.T) {
	activities := []Activity{
		movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 7200, true),
		movie(2, at(time.March, 2, 20), at(time.March, 2, 21), 3600, false),
		episode(10, 5, at(time.April, 1, 20), at(time.April, 1, 21), true),
		episode(11, 5, at(time.April, 1, 21), at(time.April, 1, 22), true),
		episode(20, 6, at(time.April, 2, 20), at(time.April, 2, 21), false),
	}
	review := Build(activities, nil, 2026, utc)
	if review.MoviesFinished != 1 || review.EpisodesFinished != 2 || review.SeriesWatched != 2 {
		t.Fatalf("movies %d, episodes %d, series %d", review.MoviesFinished, review.EpisodesFinished, review.SeriesWatched)
	}
}

func TestTopSeriesIsTheOneWithMostWatchTime(t *testing.T) {
	activities := []Activity{
		episode(10, 5, at(time.April, 1, 20), at(time.April, 1, 21), true),
		episode(20, 6, at(time.April, 2, 20), at(time.April, 2, 21), true),
		episode(21, 6, at(time.April, 3, 20), at(time.April, 3, 21), true),
	}
	top := Build(activities, nil, 2026, utc).TopSeries
	if top == nil || top.SeriesID != 6 || top.Episodes != 2 || top.Seconds != 4800 {
		t.Fatalf("top series = %+v", top)
	}
}

func TestTopGenresShareTheWatchTime(t *testing.T) {
	activities := []Activity{
		movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 7200, true),
		episode(10, 5, at(time.April, 1, 20), at(time.April, 1, 21), true),
	}
	genres := map[library.MediaRef][]string{
		{MediaType: library.MediaTypeMovie, MediaID: 1}: {"Drama", "Crime"},
		seriesRefOf(5): {"Drama"},
	}
	stats := Build(activities, genres, 2026, utc).TopGenres
	if len(stats) != 2 || stats[0].Name != "Drama" || stats[0].Seconds != 9600 || stats[1].Name != "Crime" {
		t.Fatalf("genres = %+v", stats)
	}
	if share := stats[0].Share + stats[1].Share; share < 0.999 || share > 1.001 {
		t.Fatalf("shares add up to %v, want 1", share)
	}
}

func TestTopGenresAreLimited(t *testing.T) {
	activities := []Activity{movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 7200, true)}
	genres := map[library.MediaRef][]string{
		{MediaType: library.MediaTypeMovie, MediaID: 1}: {"A", "B", "C", "D", "E", "F", "G"},
	}
	if stats := Build(activities, genres, 2026, utc).TopGenres; len(stats) != topGenreLimit {
		t.Fatalf("%d genres, want %d", len(stats), topGenreLimit)
	}
}

func TestBingeIsTheMostEpisodesOfOneSeriesInOneNight(t *testing.T) {
	activities := []Activity{
		// Friday night into Saturday 2 am: one night, three episodes.
		episode(1, 5, at(time.May, 1, 21), at(time.May, 1, 22), true),
		episode(2, 5, at(time.May, 1, 22), at(time.May, 1, 23), true),
		episode(3, 5, at(time.May, 2, 0), at(time.May, 2, 2), true),
		// Saturday evening: a new night, two episodes.
		episode(4, 5, at(time.May, 2, 20), at(time.May, 2, 21), true),
		episode(5, 5, at(time.May, 2, 21), at(time.May, 2, 22), true),
		// Another series, one episode only.
		episode(6, 7, at(time.May, 3, 20), at(time.May, 3, 21), true),
	}
	binge := Build(activities, nil, 2026, utc).LongestBinge
	if binge == nil || binge.SeriesID != 5 || binge.Episodes != 3 || binge.Night != "2026-05-01" {
		t.Fatalf("binge = %+v", binge)
	}
}

func TestBingeNeedsAtLeastTwoFinishedEpisodes(t *testing.T) {
	activities := []Activity{
		episode(1, 5, at(time.May, 1, 21), at(time.May, 1, 22), true),
		episode(2, 5, at(time.May, 1, 22), at(time.May, 1, 23), false),
	}
	if binge := Build(activities, nil, 2026, utc).LongestBinge; binge != nil {
		t.Fatalf("binge = %+v, want none", binge)
	}
}

func TestNightsFollowTheProfilesTimeZone(t *testing.T) {
	// 23:30 and 01:30 UTC are 01:30 and 03:30 in Berlin (UTC+2): still one night there.
	berlin := time.FixedZone("berlin", 2*3600)
	activities := []Activity{
		episode(1, 5, time.Date(2026, 5, 1, 22, 0, 0, 0, utc), time.Date(2026, 5, 1, 23, 30, 0, 0, utc), true),
		episode(2, 5, time.Date(2026, 5, 2, 0, 30, 0, 0, utc), time.Date(2026, 5, 2, 1, 30, 0, 0, utc), true),
	}
	binge := Build(activities, nil, 2026, berlin).LongestBinge
	if binge == nil || binge.Episodes != 2 || binge.Night != "2026-05-01" {
		t.Fatalf("binge = %+v", binge)
	}
}

func TestRewatchIsATitleStartedLongBeforeItsLastWatch(t *testing.T) {
	activities := []Activity{
		movie(1, time.Date(2024, 5, 1, 20, 0, 0, 0, utc), at(time.March, 1, 22), 7200, true),
		movie(2, at(time.February, 1, 20), at(time.February, 1, 22), 7200, true),
		episode(10, 5, time.Date(2025, 1, 1, 20, 0, 0, 0, utc), at(time.April, 1, 21), true),
		episode(11, 5, time.Date(2025, 1, 2, 20, 0, 0, 0, utc), at(time.April, 2, 21), true),
	}
	rewatched := Build(activities, nil, 2026, utc).MostRewatched
	if rewatched == nil || rewatched.Title != seriesRefOf(5) || rewatched.Count != 2 {
		t.Fatalf("rewatched = %+v", rewatched)
	}
}

func TestNoRewatchWhenEverythingWasWatchedOnce(t *testing.T) {
	activities := []Activity{movie(1, at(time.March, 1, 20), at(time.March, 1, 22), 7200, true)}
	if rewatched := Build(activities, nil, 2026, utc).MostRewatched; rewatched != nil {
		t.Fatalf("rewatched = %+v", rewatched)
	}
}

func TestFirstAndLastTitleOfTheYear(t *testing.T) {
	activities := []Activity{
		movie(2, at(time.June, 1, 20), at(time.June, 1, 22), 7200, true),
		// Started last year, last watched in January: its first moment this year is January.
		movie(1, time.Date(2025, 12, 20, 20, 0, 0, 0, utc), at(time.January, 2, 22), 7200, true),
		episode(10, 5, at(time.December, 20, 20), at(time.December, 30, 22), true),
	}
	review := Build(activities, nil, 2026, utc)
	if review.First == nil || review.First.Title.MediaID != 1 {
		t.Fatalf("first = %+v, want the film watched in January", review.First)
	}
	if review.Last == nil || review.Last.Title != seriesRefOf(5) {
		t.Fatalf("last = %+v, want the series watched on December 30", review.Last)
	}
}
