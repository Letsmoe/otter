package calendar

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/cache"
	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/hooks"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
)

func TestTokenIsTakenFromThePath(t *testing.T) {
	cases := map[string]struct {
		token string
		ok    bool
	}{
		"/api/calendar/abc123.ics":     {"abc123", true},
		"/api/calendar/.ics":           {"", false},
		"/api/calendar/abc123":         {"", false},
		"/api/calendar/a/b.ics":        {"", false},
		"/api/stream/abc123.ics":       {"", false},
		"/api/calendar/abc123.ics.ics": {"abc123.ics", true},
	}
	for path, want := range cases {
		token, ok := tokenOf(path)
		if token != want.token || ok != want.ok {
			t.Errorf("%s: got %q, %v; want %q, %v", path, token, ok, want.token, want.ok)
		}
	}
}

func TestEpisodeEventsAreNamedAfterSeriesAndEpisode(t *testing.T) {
	series := library.Title{Title: "Bosch"}
	named := library.Episode{TMDBID: 5, SeasonNumber: 7, EpisodeNumber: 3, Name: "Long Way Home", AirDate: "2026-03-12"}
	unnamed := library.Episode{TMDBID: 6, SeasonNumber: 7, EpisodeNumber: 4, AirDate: "2026-03-19"}

	first, second := episodeEvent(series, named), episodeEvent(series, unnamed)

	if first.Summary != "Bosch: S7E3 Long Way Home" || first.UID != "episode-5@otter" || first.Day.Format(dayFormat) != "2026-03-12" {
		t.Errorf("named: %+v", first)
	}
	if second.Summary != "Bosch: S7E4" {
		t.Errorf("unnamed: %+v", second)
	}
}

func newTestSetup(t *testing.T) (*Service, *profiles.Service, *db.DB) {
	t.Helper()
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	profileService := profiles.NewService(database, cache.New(ctx, database), hooks.New(t.TempDir(), nil))
	return NewService(database, profileService, nil), profileService, database
}

func TestOnlyEpisodesWithinTheHorizonAreListed(t *testing.T) {
	ctx := context.Background()
	service, _, database := newTestSetup(t)
	now := time.Date(2026, 3, 1, 9, 0, 0, 0, time.UTC)
	insert := `INSERT INTO episode (tmdb_id, series_id, season_number, episode_number, name, air_date)
		VALUES ($id, 1, 1, $number, 'x', $air_date)`
	for id, airDate := range map[int]string{1: "2026-02-28", 2: "2026-03-01", 3: "2026-04-15", 4: "2026-04-26", 5: "2026-04-27"} {
		if _, err := database.Exec(ctx, insert, db.Vars{"id": id, "number": id, "air_date": airDate}); err != nil {
			t.Fatal(err)
		}
	}

	episodes, err := service.airingEpisodes(ctx, 1, now)
	if err != nil {
		t.Fatal(err)
	}

	var ids []int
	for _, episode := range episodes {
		ids = append(ids, episode.TMDBID)
	}
	if len(ids) != 3 || ids[0] != 2 || ids[1] != 3 || ids[2] != 4 {
		t.Fatalf("got %v", ids)
	}
}

func TestOnlyFollowedSeriesCount(t *testing.T) {
	ctx := context.Background()
	service, profileService, database := newTestSetup(t)
	profile, err := profileService.Create(ctx, "Alex", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	itoa := strconv.Itoa
	statements := []string{
		`INSERT INTO episode (tmdb_id, series_id, season_number, episode_number) VALUES (10, 100, 1, 1)`,
		`INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds, duration_seconds, completed, started_at, updated_at)
			VALUES (` + itoa(profile.ID) + `, 'episode', 10, 5, 50, 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')`,
		`INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES (` + itoa(profile.ID) + `, 'series', 200, '2026-01-01T00:00:00Z')`,
		`INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES (` + itoa(profile.ID) + `, 'movie', 300, '2026-01-01T00:00:00Z')`,
	}
	for _, statement := range statements {
		if _, err := database.Exec(ctx, statement, nil); err != nil {
			t.Fatal(err)
		}
	}

	ids, err := service.followedSeries(ctx, profile.ID)
	if err != nil {
		t.Fatal(err)
	}

	if len(ids) != 2 {
		t.Fatalf("got %v", ids)
	}
	seen := map[int]bool{ids[0]: true, ids[1]: true}
	if !seen[100] || !seen[200] {
		t.Fatalf("got %v", ids)
	}
}

func TestTheHandlerAnswersOnlyForAKnownToken(t *testing.T) {
	ctx := context.Background()
	service, profileService, _ := newTestSetup(t)
	profile, err := profileService.Create(ctx, "Alex", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	token, err := profileService.CalendarToken(ctx, profile.ID)
	if err != nil {
		t.Fatal(err)
	}

	known := httptest.NewRecorder()
	service.Handler().ServeHTTP(known, httptest.NewRequest(http.MethodGet, RoutePrefix+token+FileSuffix, nil))
	unknown := httptest.NewRecorder()
	service.Handler().ServeHTTP(unknown, httptest.NewRequest(http.MethodGet, RoutePrefix+"0000"+FileSuffix, nil))

	if known.Code != http.StatusOK || !strings.HasPrefix(known.Header().Get("Content-Type"), "text/calendar") {
		t.Fatalf("known token: %d %v", known.Code, known.Header())
	}
	if !strings.Contains(known.Body.String(), "X-WR-CALNAME:Otter: Alex") {
		t.Errorf("feed: %s", known.Body.String())
	}
	if unknown.Code != http.StatusNotFound {
		t.Fatalf("unknown token: %d", unknown.Code)
	}
}
