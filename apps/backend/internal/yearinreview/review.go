// Package yearinreview builds "Your year in Otter": a look back at one
// profile's watch history for a calendar year. It only reads the history
// (one row per movie or episode), so some figures are estimates; see Build.
package yearinreview

import (
	"cmp"
	"slices"
	"time"

	"github.com/Letsmoe/otter/internal/library"
)

const (
	// completedFraction is the share of the runtime that counts as finished (same as profiles).
	completedFraction = 0.9
	// minimumWatchedSeconds drops rows that only opened the player.
	minimumWatchedSeconds = 60
	// nightShift moves the start of a "night" to 5 am, so a binge past midnight is one night.
	nightShift = 5 * time.Hour
	// minimumBingeEpisodes is how many episodes of one series in a night make a binge.
	minimumBingeEpisodes = 2
	// rewatchGap is how long before its latest activity a title must have been started to count as watched again.
	rewatchGap = 30 * 24 * time.Hour
	topGenreLimit = 5
)

// Activity is one row of a profile's watch history: a movie or an episode.
type Activity struct {
	Media library.MediaRef
	// SeriesID is the series of an episode; 0 for a movie.
	SeriesID        int
	ProgressSeconds float64
	DurationSeconds float64
	Completed       bool
	StartedAt       time.Time
	UpdatedAt       time.Time
}

// Title is the movie or series an activity belongs to.
func (activity Activity) Title() library.MediaRef {
	if activity.SeriesID != 0 {
		return library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: activity.SeriesID}
	}
	return activity.Media
}

func (activity Activity) isEpisode() bool {
	return activity.SeriesID != 0
}

// watchedSeconds is the time spent, never more than the runtime.
func (activity Activity) watchedSeconds() float64 {
	if activity.DurationSeconds > 0 {
		return min(activity.ProgressSeconds, activity.DurationSeconds)
	}
	return activity.ProgressSeconds
}

func (activity Activity) isFinished() bool {
	if activity.Completed {
		return true
	}
	return activity.DurationSeconds > 0 && activity.ProgressSeconds/activity.DurationSeconds >= completedFraction
}

// SeriesStat is the series watched the most.
type SeriesStat struct {
	SeriesID int
	Seconds  float64
	Episodes int
}

// GenreStat is a genre's share of the watch time.
type GenreStat struct {
	Name    string
	Seconds float64
	// Share of the time that has genres, 0..1.
	Share float64
}

// Binge is the most episodes of one series in one night.
type Binge struct {
	SeriesID int
	Episodes int
	// Night is the date the night began (YYYY-MM-DD, in the profile's time zone).
	Night string
}

// Rewatched is the title watched again the most.
type Rewatched struct {
	Title library.MediaRef
	// Count is how many of its episodes (1 for a movie) were watched again.
	Count int
}

// Moment is a title and when the profile watched it.
type Moment struct {
	Title library.MediaRef
	At    time.Time
}

// Review is one profile's year.
type Review struct {
	Year             int
	HasActivity      bool
	WatchedSeconds   float64
	MoviesFinished   int
	EpisodesFinished int
	SeriesWatched    int
	TopSeries        *SeriesStat
	TopGenres        []GenreStat
	LongestBinge     *Binge
	MostRewatched    *Rewatched
	First            *Moment
	Last             *Moment
}

// Build summarizes the activities of a calendar year in location. The history
// keeps one row per title, dated by its latest update, so:
//   - a title counts in the year of its last watch;
//   - a rewatch is a title started at least 30 days before its latest watch
//     (the history can't count repeats, so this is an estimate);
//   - a binge is episodes of one series whose latest watch fell into one night.
//
// genres maps a movie or series to its genres.
func Build(activities []Activity, genres map[library.MediaRef][]string, year int, location *time.Location) Review {
	yearStart := time.Date(year, time.January, 1, 0, 0, 0, 0, location)
	inYear := activitiesOfYear(activities, yearStart)
	review := Review{Year: year, HasActivity: len(inYear) > 0}
	if !review.HasActivity {
		return review
	}
	review.WatchedSeconds = totalSeconds(inYear)
	review.MoviesFinished, review.EpisodesFinished, review.SeriesWatched = finishedCounts(inYear)
	review.TopSeries = topSeries(inYear)
	review.TopGenres = topGenres(inYear, genres)
	review.LongestBinge = longestBinge(inYear, location)
	review.MostRewatched = mostRewatched(inYear)
	review.First, review.Last = firstAndLast(inYear, yearStart)
	return review
}

func activitiesOfYear(activities []Activity, start time.Time) []Activity {
	end := start.AddDate(1, 0, 0)
	kept := []Activity{}
	for _, activity := range activities {
		inRange := !activity.UpdatedAt.Before(start) && activity.UpdatedAt.Before(end)
		if inRange && activity.watchedSeconds() >= minimumWatchedSeconds {
			kept = append(kept, activity)
		}
	}
	return kept
}

func totalSeconds(activities []Activity) float64 {
	total := 0.0
	for _, activity := range activities {
		total += activity.watchedSeconds()
	}
	return total
}

func finishedCounts(activities []Activity) (movies, episodes, series int) {
	seenSeries := map[int]bool{}
	for _, activity := range activities {
		if activity.isEpisode() {
			seenSeries[activity.SeriesID] = true
		}
		if !activity.isFinished() {
			continue
		}
		if activity.isEpisode() {
			episodes++
			continue
		}
		movies++
	}
	return movies, episodes, len(seenSeries)
}

func topSeries(activities []Activity) *SeriesStat {
	bySeries := map[int]*SeriesStat{}
	for _, activity := range activities {
		if !activity.isEpisode() {
			continue
		}
		stat := bySeries[activity.SeriesID]
		if stat == nil {
			stat = &SeriesStat{SeriesID: activity.SeriesID}
			bySeries[activity.SeriesID] = stat
		}
		stat.Seconds += activity.watchedSeconds()
		stat.Episodes++
	}
	var best *SeriesStat
	for _, stat := range bySeries {
		if isBetterSeries(stat, best) {
			best = stat
		}
	}
	return best
}

// isBetterSeries prefers more watch time, then the lower series id.
func isBetterSeries(candidate, best *SeriesStat) bool {
	if best == nil {
		return true
	}
	if candidate.Seconds != best.Seconds {
		return candidate.Seconds > best.Seconds
	}
	return candidate.SeriesID < best.SeriesID
}

func topGenres(activities []Activity, genres map[library.MediaRef][]string) []GenreStat {
	seconds := map[string]float64{}
	total := 0.0
	for _, activity := range activities {
		for _, genre := range genres[activity.Title()] {
			seconds[genre] += activity.watchedSeconds()
			total += activity.watchedSeconds()
		}
	}
	stats := make([]GenreStat, 0, len(seconds))
	for name, genreSeconds := range seconds {
		stats = append(stats, GenreStat{Name: name, Seconds: genreSeconds, Share: genreSeconds / total})
	}
	slices.SortFunc(stats, func(left, right GenreStat) int {
		if left.Seconds != right.Seconds {
			return cmp.Compare(right.Seconds, left.Seconds)
		}
		return cmp.Compare(left.Name, right.Name)
	})
	if len(stats) > topGenreLimit {
		stats = stats[:topGenreLimit]
	}
	return stats
}

type bingeKey struct {
	seriesID int
	night    string
}

func longestBinge(activities []Activity, location *time.Location) *Binge {
	counts := map[bingeKey]int{}
	for _, activity := range activities {
		if activity.isEpisode() && activity.isFinished() {
			key := bingeKey{activity.SeriesID, nightOf(activity.UpdatedAt, location)}
			counts[key]++
		}
	}
	var best *Binge
	for key, count := range counts {
		candidate := Binge{SeriesID: key.seriesID, Episodes: count, Night: key.night}
		if count >= minimumBingeEpisodes && isBetterBinge(candidate, best) {
			best = &candidate
		}
	}
	return best
}

// isBetterBinge prefers more episodes, then the earlier night, then the lower series id.
func isBetterBinge(candidate Binge, best *Binge) bool {
	if best == nil {
		return true
	}
	if candidate.Episodes != best.Episodes {
		return candidate.Episodes > best.Episodes
	}
	if candidate.Night != best.Night {
		return candidate.Night < best.Night
	}
	return candidate.SeriesID < best.SeriesID
}

// nightOf is the date a night began: before 5 am still belongs to the day before.
func nightOf(moment time.Time, location *time.Location) string {
	return moment.In(location).Add(-nightShift).Format("2006-01-02")
}

func mostRewatched(activities []Activity) *Rewatched {
	counts := map[library.MediaRef]int{}
	for _, activity := range activities {
		if activity.isFinished() && activity.UpdatedAt.Sub(activity.StartedAt) >= rewatchGap {
			counts[activity.Title()]++
		}
	}
	var best *Rewatched
	for title, count := range counts {
		candidate := Rewatched{Title: title, Count: count}
		if isBetterRewatch(candidate, best) {
			best = &candidate
		}
	}
	return best
}

// isBetterRewatch prefers more repeats, then the lower id.
func isBetterRewatch(candidate Rewatched, best *Rewatched) bool {
	if best == nil {
		return true
	}
	if candidate.Count != best.Count {
		return candidate.Count > best.Count
	}
	return refLess(candidate.Title, best.Title)
}

func refLess(left, right library.MediaRef) bool {
	if left.MediaType != right.MediaType {
		return left.MediaType < right.MediaType
	}
	return left.MediaID < right.MediaID
}

// firstAndLast are the titles watched first and last in the year: by the time
// each was started (its last watch when it was started before the year) and
// by the time each was last watched.
func firstAndLast(activities []Activity, yearStart time.Time) (first, last *Moment) {
	for _, activity := range activities {
		started := Moment{Title: activity.Title(), At: startInYear(activity, yearStart)}
		if first == nil || started.At.Before(first.At) {
			first = &started
		}
		watched := Moment{Title: activity.Title(), At: activity.UpdatedAt}
		if last == nil || watched.At.After(last.At) {
			last = &watched
		}
	}
	return first, last
}

func startInYear(activity Activity, yearStart time.Time) time.Time {
	if activity.StartedAt.Before(yearStart) {
		return activity.UpdatedAt
	}
	return activity.StartedAt
}
