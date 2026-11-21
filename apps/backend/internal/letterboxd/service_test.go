package letterboxd

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/cache"
	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/hooks"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
	"github.com/Letsmoe/otter/internal/tmdb"
)

// fakeCatalog hands out movie refs by TMDB id.
type fakeCatalog struct{}

func (fakeCatalog) SaveMovieStub(_ context.Context, result tmdb.SearchResult) (library.MediaRef, error) {
	return library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: result.ID}, nil
}

func (fakeCatalog) EnsureMovie(_ context.Context, tmdbID int) (library.MediaRef, error) {
	return library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: tmdbID}, nil
}

type testWorld struct {
	service   *Service
	profiles  *profiles.Service
	profileID int
}

func newTestWorld(t *testing.T, finder MovieFinder, client *http.Client) testWorld {
	t.Helper()
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	profileService := profiles.NewService(database, cache.New(ctx, database), hooks.New(t.TempDir(), nil))
	profile, err := profileService.Create(ctx, "Ann", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	if client == nil {
		client = http.DefaultClient
	}
	service := NewService(database, finder, fakeCatalog{}, profileService, client)
	return testWorld{service: service, profiles: profileService, profileID: profile.ID}
}

func waitForImport(t *testing.T, service *Service, profileID int) ImportStatus {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		status := service.ImportOf(profileID)
		if status.State != ImportRunning {
			return status
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("the import did not finish")
	return ImportStatus{}
}

func moviesFinder() *fakeFinder {
	return &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Heat|1995|":    {movie(949, "Heat", "Heat", "1995-12-15", 70)},
		"Stalker|1979|": {movie(1398, "Stalker", "Сталкер", "1979-05-25", 40)},
		"Alien|1979|":   {movie(348, "Alien", "Alien", "1979-05-25", 90)},
	}}
}

func exportZip(t *testing.T) []byte {
	return buildZip(t, map[string]string{
		"watched.csv": "Date,Name,Year,Letterboxd URI\n2024-01-05,Heat,1995,https://boxd.it/a\n2024-02-01,Obscure Home Movie,2020,https://boxd.it/z\n",
		"ratings.csv": "Date,Name,Year,Letterboxd URI,Rating\n2024-01-06,Heat,1995,https://boxd.it/a,4.5\n",
		"diary.csv": "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n" +
			"2024-01-06,Heat,1995,https://boxd.it/a,4.5,,,2024-01-05\n" +
			"2024-02-02,Alien,1979,https://boxd.it/c,3,,,2024-02-02\n",
		"watchlist.csv": "Date,Name,Year,Letterboxd URI\n2024-04-01,Stalker,1979,https://boxd.it/b\n",
	})
}

func TestAnImportMarksWatchedRatesAndFillsMyList(t *testing.T) {
	ctx := context.Background()
	world := newTestWorld(t, moviesFinder(), nil)

	if _, err := world.service.StartImport(world.profileID, exportZip(t)); err != nil {
		t.Fatal(err)
	}
	status := waitForImport(t, world.service, world.profileID)
	if status.State != ImportDone {
		t.Fatalf("state %s: %s", status.State, status.Error)
	}
	if status.Total != 4 || status.Matched != 3 || status.Watched != 2 || status.Rated != 2 || status.Watchlisted != 1 {
		t.Fatalf("status %+v", status)
	}
	if len(status.NotMatched) != 1 || status.NotMatched[0].Name != "Obscure Home Movie" {
		t.Fatalf("not matched: %+v", status.NotMatched)
	}

	heat := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 949}
	entry, err := world.profiles.WatchEntry(ctx, world.profileID, heat)
	if err != nil || entry == nil || !entry.Completed {
		t.Fatalf("heat not watched: %+v, %v", entry, err)
	}
	if entry.UpdatedAt != "2024-01-05T12:00:00.000Z" {
		t.Errorf("watched on %s, want the diary's day", entry.UpdatedAt)
	}
	rating, err := world.profiles.Rating(ctx, world.profileID, heat)
	if err != nil || rating == nil || rating.Rating != 9 {
		t.Fatalf("heat rating %+v, %v", rating, err)
	}
	watchlist, err := world.profiles.Watchlist(ctx, world.profileID)
	if err != nil || len(watchlist) != 1 || watchlist[0].MediaID != 1398 {
		t.Fatalf("watchlist %+v, %v", watchlist, err)
	}
}

func TestImportingTwiceChangesNothingTheSecondTime(t *testing.T) {
	world := newTestWorld(t, moviesFinder(), nil)
	for range 2 {
		if _, err := world.service.StartImport(world.profileID, exportZip(t)); err != nil {
			t.Fatal(err)
		}
		waitForImport(t, world.service, world.profileID)
	}
	status := world.service.ImportOf(world.profileID)
	if status.Watched != 0 || status.Rated != 0 || status.Watchlisted != 0 {
		t.Fatalf("a second import added %+v", status)
	}
}

func TestAnImportKeepsWhatOtterRecordedItself(t *testing.T) {
	ctx := context.Background()
	world := newTestWorld(t, moviesFinder(), nil)
	heat := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 949}
	if _, err := world.profiles.RecordProgress(ctx, world.profileID, heat, 7000, 7200); err != nil {
		t.Fatal(err)
	}
	if err := world.profiles.SetRating(ctx, world.profileID, heat, 3); err != nil {
		t.Fatal(err)
	}

	if _, err := world.service.StartImport(world.profileID, exportZip(t)); err != nil {
		t.Fatal(err)
	}
	waitForImport(t, world.service, world.profileID)

	entry, _ := world.profiles.WatchEntry(ctx, world.profileID, heat)
	if entry.ProgressSeconds != 7000 || entry.DurationSeconds != 7200 {
		t.Errorf("a real watch was overwritten: %+v", entry)
	}
	rating, _ := world.profiles.Rating(ctx, world.profileID, heat)
	if rating.Rating != 3 {
		t.Errorf("a newer rating made in Otter was overwritten: %v", rating.Rating)
	}
}

func TestAnExportThatIsNoneIsRefusedAtOnce(t *testing.T) {
	world := newTestWorld(t, moviesFinder(), nil)
	if _, err := world.service.StartImport(world.profileID, []byte("nope")); err == nil {
		t.Fatal("expected an error")
	}
	if status := world.service.ImportOf(world.profileID); status.State != ImportIdle {
		t.Errorf("state %s", status.State)
	}
}

func TestAProfileHasOneImportAtATime(t *testing.T) {
	world := newTestWorld(t, moviesFinder(), nil)
	if err := world.service.beginImport(world.profileID, 10); err != nil {
		t.Fatal(err)
	}
	if _, err := world.service.StartImport(world.profileID, exportZip(t)); err != ErrImportRunning {
		t.Fatalf("err = %v, want ErrImportRunning", err)
	}
}

func feedServer(t *testing.T) *http.Client {
	t.Helper()
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		_, _ = writer.Write([]byte(sampleFeed))
	}))
	t.Cleanup(server.Close)
	client := server.Client()
	client.Transport = redirectingTransport{target: server.Listener.Addr().String(), inner: client.Transport}
	return client
}

// redirectingTransport sends every request to the test server, whatever its host.
type redirectingTransport struct {
	target string
	inner  http.RoundTripper
}

func (r redirectingTransport) RoundTrip(request *http.Request) (*http.Response, error) {
	redirected := request.Clone(request.Context())
	redirected.URL.Host = r.target
	return r.inner.RoundTrip(redirected)
}

func TestSyncRecordsTheFeedsNewWatchesAndRatings(t *testing.T) {
	ctx := context.Background()
	world := newTestWorld(t, moviesFinder(), feedServer(t))
	if _, err := world.service.Connect(ctx, world.profileID, "https://letterboxd.com/ann/"); err != nil {
		t.Fatal(err)
	}

	connection, err := world.service.SyncNow(ctx, world.profileID)
	if err != nil {
		t.Fatal(err)
	}
	if connection.LastError != "" || connection.LastWatched != 2 || connection.LastRated != 1 || connection.LastSyncedAt == "" {
		t.Fatalf("connection %+v", connection)
	}
	heat := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 949}
	rating, _ := world.profiles.Rating(ctx, world.profileID, heat)
	if rating == nil || rating.Rating != 9 {
		t.Fatalf("rating %+v", rating)
	}

	again, err := world.service.SyncNow(ctx, world.profileID)
	if err != nil || again.LastWatched != 0 || again.LastRated != 0 {
		t.Fatalf("a second sync added %+v, %v", again, err)
	}
}

func TestAFailedSyncIsKeptAsTheLastError(t *testing.T) {
	ctx := context.Background()
	failing := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		http.Error(writer, "no", http.StatusForbidden)
	}))
	defer failing.Close()
	client := failing.Client()
	client.Transport = redirectingTransport{target: failing.Listener.Addr().String(), inner: client.Transport}
	world := newTestWorld(t, moviesFinder(), client)
	if _, err := world.service.Connect(ctx, world.profileID, "ann"); err != nil {
		t.Fatal(err)
	}

	connection, err := world.service.SyncNow(ctx, world.profileID)
	if err != nil || connection.LastError == "" {
		t.Fatalf("connection %+v, err %v", connection, err)
	}
}

func TestSyncNeedsAnAccount(t *testing.T) {
	world := newTestWorld(t, moviesFinder(), nil)
	if _, err := world.service.SyncNow(context.Background(), world.profileID); err != ErrNotConnected {
		t.Fatalf("err = %v", err)
	}
}

func TestConnectingAnotherUsernameStartsOver(t *testing.T) {
	ctx := context.Background()
	world := newTestWorld(t, moviesFinder(), feedServer(t))
	if _, err := world.service.Connect(ctx, world.profileID, "ann"); err != nil {
		t.Fatal(err)
	}
	if _, err := world.service.SyncNow(ctx, world.profileID); err != nil {
		t.Fatal(err)
	}

	same, _ := world.service.Connect(ctx, world.profileID, "ann")
	if same.LastSyncedAt == "" {
		t.Error("connecting the same username again forgot the last sync")
	}
	other, _ := world.service.Connect(ctx, world.profileID, "bob")
	if other.Username != "bob" || other.LastSyncedAt != "" || other.LastWatched != 0 {
		t.Errorf("another username kept the old result: %+v", other)
	}

	if err := world.service.Disconnect(ctx, world.profileID); err != nil {
		t.Fatal(err)
	}
	if connection, _ := world.service.ConnectionOf(ctx, world.profileID); connection != nil {
		t.Error("still connected")
	}
}
