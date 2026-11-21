package library

import (
	"context"
	"log"
	"sort"
	"sync"
	"time"
)

const (
	airDateLayout = "2006-01-02"
	// Only the newest seasons can have episodes that have not aired yet.
	upcomingSeasonsChecked = 2
	// How many series are looked up at the same time, to be kind to TMDB.
	upcomingParallelLookups = 4
)

// UpcomingEpisode is an episode that has not aired yet, with its series.
type UpcomingEpisode struct {
	Series  Title
	Episode Episode
}

// UpcomingEpisodes lists the episodes of the given series that air from today
// until the given number of days later, soonest first. Series TMDB lists as
// ended are skipped; a series that cannot be looked up is skipped too.
func (s *Service) UpcomingEpisodes(ctx context.Context, seriesIDs []int, today time.Time, days int) ([]UpcomingEpisode, error) {
	var mutex sync.Mutex
	var group sync.WaitGroup
	slots := make(chan struct{}, upcomingParallelLookups)
	upcoming := []UpcomingEpisode{}
	for _, seriesID := range seriesIDs {
		group.Add(1)
		slots <- struct{}{}
		go func(seriesID int) {
			defer group.Done()
			defer func() { <-slots }()
			found, err := s.upcomingOfSeries(ctx, seriesID, today, days)
			if err != nil {
				log.Printf("library: upcoming episodes of series %d: %v", seriesID, err)
				return
			}
			mutex.Lock()
			upcoming = append(upcoming, found...)
			mutex.Unlock()
		}(seriesID)
	}
	group.Wait()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	sortUpcoming(upcoming)
	return upcoming, nil
}

func (s *Service) upcomingOfSeries(ctx context.Context, seriesID int, today time.Time, days int) ([]UpcomingEpisode, error) {
	series, err := s.EnsureTitle(ctx, MediaRef{MediaType: MediaTypeSeries, MediaID: seriesID})
	if err != nil || series == nil || hasEnded(*series) {
		return nil, err
	}
	seasons, err := s.Store.Seasons(ctx, seriesID)
	if err != nil {
		return nil, err
	}
	episodes := []Episode{}
	for _, seasonNumber := range NewestRegularSeasonNumbers(seasons, upcomingSeasonsChecked) {
		seasonEpisodes, err := s.EnsureSeason(ctx, seriesID, seasonNumber)
		if err != nil {
			return nil, err
		}
		episodes = append(episodes, seasonEpisodes...)
	}
	return upcomingWithin(*series, episodes, today, days), nil
}

func hasEnded(series Title) bool {
	return series.Status == "Ended" || series.Status == "Canceled"
}

// NewestRegularSeasonNumbers returns the numbers of the last count seasons
// without specials, oldest first. seasons come ordered with specials last.
func NewestRegularSeasonNumbers(seasons []Season, count int) []int {
	numbers := []int{}
	for _, season := range seasons {
		if season.SeasonNumber > 0 {
			numbers = append(numbers, season.SeasonNumber)
		}
	}
	if len(numbers) > count {
		numbers = numbers[len(numbers)-count:]
	}
	return numbers
}

// upcomingWithin keeps the regular episodes of the series that air on or after
// today and no later than days after it.
func upcomingWithin(series Title, episodes []Episode, today time.Time, days int) []UpcomingEpisode {
	lastDay := today.AddDate(0, 0, days)
	upcoming := []UpcomingEpisode{}
	for _, episode := range episodes {
		airDate, err := time.Parse(airDateLayout, episode.AirDate)
		if err != nil || episode.SeasonNumber == 0 {
			continue
		}
		if airDate.Before(today) || airDate.After(lastDay) {
			continue
		}
		upcoming = append(upcoming, UpcomingEpisode{Series: series, Episode: episode})
	}
	return upcoming
}

// sortUpcoming orders by air date, then series title, then episode number.
func sortUpcoming(upcoming []UpcomingEpisode) {
	sort.Slice(upcoming, func(first, second int) bool {
		a, b := upcoming[first], upcoming[second]
		if a.Episode.AirDate != b.Episode.AirDate {
			return a.Episode.AirDate < b.Episode.AirDate
		}
		if a.Series.TMDBID != b.Series.TMDBID {
			return a.Series.Title < b.Series.Title
		}
		return a.Episode.EpisodeNumber < b.Episode.EpisodeNumber
	})
}
