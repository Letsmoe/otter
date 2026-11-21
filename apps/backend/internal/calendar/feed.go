package calendar

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
)

// RoutePrefix is where Handler is mounted; the feed of a profile is RoutePrefix + token + FileSuffix.
const (
	RoutePrefix = "/api/calendar/"
	FileSuffix  = ".ics"
)

const (
	// How far ahead episodes are listed.
	horizon = 8 * 7 * 24 * time.Hour
	// A built feed is reused for this long: a calendar app asks often, TMDB is asked rarely.
	feedTTL = 15 * time.Minute
	// Only the newest seasons of a series can hold episodes that haven't aired yet.
	newestSeasons = 2
	dayFormat     = "2006-01-02"
)

type cachedFeed struct {
	document string
	builtAt  time.Time
}

// Service builds the feeds.
type Service struct {
	database *db.DB
	profiles *profiles.Service
	library  *library.Service

	mutex sync.Mutex
	feeds map[int]cachedFeed
}

func NewService(database *db.DB, profileService *profiles.Service, libraryService *library.Service) *Service {
	return &Service{database: database, profiles: profileService, library: libraryService, feeds: map[int]cachedFeed{}}
}

// Handler serves GET /api/calendar/<token>.ics. An unknown token is a plain 404, so guessing tells nothing.
func (s *Service) Handler() http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet && request.Method != http.MethodHead {
			http.Error(response, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		token, ok := tokenOf(request.URL.Path)
		if !ok {
			http.NotFound(response, request)
			return
		}
		profile, err := s.profiles.ProfileOfCalendarToken(request.Context(), token)
		if err != nil {
			http.NotFound(response, request)
			return
		}
		document, err := s.Document(request.Context(), *profile, time.Now())
		if err != nil {
			log.Printf("calendar: feed of profile %d: %v", profile.ID, err)
			http.Error(response, "could not build the calendar", http.StatusInternalServerError)
			return
		}
		response.Header().Set("Content-Type", "text/calendar; charset=utf-8")
		response.Header().Set("Cache-Control", "max-age=900")
		_, _ = response.Write([]byte(document))
	})
}

// tokenOf takes the secret out of a /api/calendar/<token>.ics path.
func tokenOf(path string) (string, bool) {
	name, found := strings.CutPrefix(path, RoutePrefix)
	if !found {
		return "", false
	}
	token, found := strings.CutSuffix(name, FileSuffix)
	if !found || token == "" || strings.Contains(token, "/") {
		return "", false
	}
	return token, true
}

// Document is the profile's feed as iCalendar text, built at most every feedTTL.
func (s *Service) Document(ctx context.Context, profile profiles.Profile, now time.Time) (string, error) {
	s.mutex.Lock()
	cached, found := s.feeds[profile.ID]
	s.mutex.Unlock()
	if found && now.Sub(cached.builtAt) < feedTTL {
		return cached.document, nil
	}
	events, err := s.Events(ctx, profile.ID, now)
	if err != nil {
		return "", err
	}
	document := Render("Otter: "+profile.Name, events, now, feedTTL)
	s.mutex.Lock()
	s.feeds[profile.ID] = cachedFeed{document: document, builtAt: now}
	s.mutex.Unlock()
	return document, nil
}

// Events lists the episodes of the series the profile watches or has on My List that air within the horizon.
func (s *Service) Events(ctx context.Context, profileID int, now time.Time) ([]Event, error) {
	seriesIDs, err := s.followedSeries(ctx, profileID)
	if err != nil {
		return nil, err
	}
	events := []Event{}
	for _, seriesID := range seriesIDs {
		seriesEvents, err := s.seriesEvents(ctx, seriesID, now)
		if err != nil {
			log.Printf("calendar: series %d: %v", seriesID, err)
			continue
		}
		events = append(events, seriesEvents...)
	}
	return events, nil
}

// followedSeries are the series the profile has watched an episode of or saved.
func (s *Service) followedSeries(ctx context.Context, profileID int) ([]int, error) {
	rows, err := s.database.Query(ctx, `SELECT episode.series_id AS series_id FROM watch_history
		JOIN episode ON episode.tmdb_id = watch_history.media_id
		WHERE watch_history.profile_id = $profile_id AND watch_history.media_type = 'episode'
		UNION
		SELECT media_id AS series_id FROM watchlist WHERE profile_id = $profile_id AND media_type = 'series'`,
		db.Vars{"profile_id": profileID})
	if err != nil {
		return nil, err
	}
	ids := make([]int, 0, len(rows))
	for _, row := range rows {
		ids = append(ids, db.AsInt(row["series_id"]))
	}
	return ids, nil
}

func (s *Service) seriesEvents(ctx context.Context, seriesID int, now time.Time) ([]Event, error) {
	series, err := s.library.EnsureTitle(ctx, library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: seriesID})
	if err != nil || series == nil {
		return nil, err
	}
	if err := s.refreshNewestSeasons(ctx, seriesID); err != nil {
		return nil, err
	}
	episodes, err := s.airingEpisodes(ctx, seriesID, now)
	if err != nil {
		return nil, err
	}
	events := make([]Event, 0, len(episodes))
	for _, episode := range episodes {
		events = append(events, episodeEvent(*series, episode))
	}
	return events, nil
}

// refreshNewestSeasons makes sure the seasons that may hold unaired episodes are stored and not a day old.
func (s *Service) refreshNewestSeasons(ctx context.Context, seriesID int) error {
	seasons, err := s.library.Store.Seasons(ctx, seriesID)
	if err != nil {
		return err
	}
	var regular []library.Season
	for _, season := range seasons {
		if season.SeasonNumber > 0 {
			regular = append(regular, season)
		}
	}
	for _, season := range regular[max(0, len(regular)-newestSeasons):] {
		if _, err := s.library.EnsureSeason(ctx, seriesID, season.SeasonNumber); err != nil {
			return err
		}
	}
	return nil
}

// airingEpisodes are the stored episodes of a series that air from today until the horizon.
func (s *Service) airingEpisodes(ctx context.Context, seriesID int, now time.Time) ([]library.Episode, error) {
	return db.QueryAs[library.Episode](ctx, s.database, `SELECT * FROM episode
		WHERE series_id = $series_id AND season_number > 0
		AND air_date >= $from AND air_date <= $until
		ORDER BY air_date, season_number, episode_number`,
		db.Vars{"series_id": seriesID, "from": now.Format(dayFormat), "until": now.Add(horizon).Format(dayFormat)})
}

func episodeEvent(series library.Title, episode library.Episode) Event {
	day, _ := time.Parse(dayFormat, episode.AirDate)
	return Event{
		UID:         fmt.Sprintf("episode-%d@otter", episode.TMDBID),
		Summary:     episodeSummary(series, episode),
		Description: episode.Overview,
		Day:         day,
	}
}

// episodeSummary reads e.g. "Bosch: S7E3 Long Way Home".
func episodeSummary(series library.Title, episode library.Episode) string {
	code := "S" + strconv.Itoa(episode.SeasonNumber) + "E" + strconv.Itoa(episode.EpisodeNumber)
	if episode.Name == "" {
		return series.Title + ": " + code
	}
	return series.Title + ": " + code + " " + episode.Name
}
