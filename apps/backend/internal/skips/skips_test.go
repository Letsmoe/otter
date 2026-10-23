package skips

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"sync/atomic"
	"testing"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
)

func newTestClient(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	client := New(cache.New(ctx, database))
	client.baseURL = server.URL
	return client
}

// The answer TheIntroDB gave for Bosch S03E03.
const boschAnswer = `{"tmdb_id":60585,"type":"tv","season":3,"episode":3,
	"intro":[{"start_ms":80250,"end_ms":140375}],"credits":[{"start_ms":2902000,"end_ms":null}]}`

func TestEpisodeReadsAndCachesSegments(t *testing.T) {
	var requests atomic.Int32
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		if r.URL.Query().Get("tmdb_id") != "60585" || r.URL.Query().Get("season") != "3" || r.URL.Query().Get("episode") != "3" {
			t.Errorf("query = %s", r.URL.RawQuery)
		}
		_, _ = w.Write([]byte(boschAnswer))
	})

	for range 2 {
		segments, err := client.Episode(context.Background(), 60585, 3, 3)
		if err != nil {
			t.Fatal(err)
		}
		if len(segments) != 2 {
			t.Fatalf("segments = %+v", segments)
		}
		intro := segments[0]
		if intro.Kind != KindIntro || intro.StartSeconds != 80.25 || intro.EndSeconds == nil || *intro.EndSeconds != 140.375 {
			t.Errorf("intro = %+v", intro)
		}
		credits := segments[1]
		if credits.Kind != KindCredits || credits.StartSeconds != 2902 || credits.EndSeconds != nil {
			t.Errorf("credits = %+v", credits)
		}
	}
	if requests.Load() != 1 {
		t.Errorf("requests = %d, want 1 (second answer cached)", requests.Load())
	}
}

func TestEpisodeNobodyTimedIsEmpty(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	})
	segments, err := client.Episode(context.Background(), 1, 1, 1)
	if err != nil || len(segments) != 0 {
		t.Fatalf("segments = %+v, %v", segments, err)
	}
}

func TestEpisodeServerErrorIsNotCached(t *testing.T) {
	var requests atomic.Int32
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		w.WriteHeader(http.StatusBadGateway)
	})
	for range 2 {
		if _, err := client.Episode(context.Background(), 1, 1, 1); err == nil {
			t.Fatal("no error for HTTP 502")
		}
	}
	if requests.Load() != 2 {
		t.Errorf("requests = %d, want 2", requests.Load())
	}
}
