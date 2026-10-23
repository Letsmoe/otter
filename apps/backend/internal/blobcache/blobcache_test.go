package blobcache

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/storage"
)

// testStore is a blob store in a temporary directory.
type testStore struct {
	t      *testing.T
	root   string
	client *storage.Client
}

// newTestStore opens a blob store in a temporary directory.
func newTestStore(t *testing.T) *testStore {
	t.Helper()
	root := t.TempDir()
	client, err := storage.New(root, "http://example.test")
	if err != nil {
		t.Fatalf("storage: %v", err)
	}
	return &testStore{t: t, root: root, client: client}
}

// put stores size bytes under key, last used at lastUsed.
func (s *testStore) put(key string, size int, lastUsed time.Time) {
	s.t.Helper()
	if err := s.client.Put(context.Background(), key, strings.NewReader(strings.Repeat("x", size)), int64(size), ""); err != nil {
		s.t.Fatalf("put %s: %v", key, err)
	}
	target := filepath.Join(s.root, filepath.FromSlash(key))
	if err := os.Chtimes(target, lastUsed, lastUsed); err != nil {
		s.t.Fatalf("chtimes: %v", err)
	}
}

// exists reports whether key is still stored.
func (s *testStore) exists(key string) bool {
	return s.client.Exists(context.Background(), key)
}

// markingSource makes each file a unit and applies per-key flags.
type markingSource struct {
	prefix      string
	pinned      map[string]bool
	active      map[string]bool
	stale       map[string]bool
	failing     map[string]bool
	invalidated []string
}

func (s *markingSource) Prefix() string {
	return s.prefix
}

func (s *markingSource) Units(ctx context.Context, files []storage.File) ([]Unit, error) {
	var units []Unit
	for _, file := range files {
		units = append(units, Unit{
			Files:  []storage.File{file},
			Pinned: s.pinned[file.Key],
			Active: s.active[file.Key],
			Stale:  s.stale[file.Key],
		})
	}
	return units, nil
}

func (s *markingSource) Invalidate(ctx context.Context, unit Unit) error {
	key := unit.Files[0].Key
	if s.failing[key] {
		return errors.New("index unavailable")
	}
	s.invalidated = append(s.invalidated, key)
	return nil
}

func TestEvictsLeastRecentlyUsedToLowWater(t *testing.T) {
	store := newTestStore(t)
	now := time.Now()
	// 5 × 30 bytes = 150 against a limit of 100: evict to ≤ 90.
	for index, name := range []string{"a", "b", "c", "d", "e"} {
		store.put("cache/"+name, 30, now.Add(-time.Duration(10-index)*time.Hour))
	}
	store.put("posters/p", 500, now.Add(-100*time.Hour))
	source := &markingSource{prefix: "cache/"}
	evictor := New(store.client, 100, source)

	report, err := evictor.Evict(context.Background())
	if err != nil {
		t.Fatalf("evict: %v", err)
	}
	if report.EvictedUnits != 2 || report.TotalBytes != 90 {
		t.Errorf("report = %+v", report)
	}
	for name, want := range map[string]bool{"cache/a": false, "cache/b": false, "cache/c": true, "cache/e": true, "posters/p": true} {
		if store.exists(name) != want {
			t.Errorf("%s exists = %v, want %v", name, !want, want)
		}
	}
	if strings.Join(source.invalidated, ",") != "cache/a,cache/b" {
		t.Errorf("invalidated %v", source.invalidated)
	}
}

func TestUnderLimitKeepsEverything(t *testing.T) {
	store := newTestStore(t)
	store.put("cache/a", 50, time.Now().Add(-time.Hour))
	evictor := New(store.client, 100, Files("cache/"))
	report, err := evictor.Evict(context.Background())
	if err != nil || report.EvictedUnits != 0 || !store.exists("cache/a") {
		t.Errorf("report = %+v, %v", report, err)
	}
}

func TestKeepsPinnedActiveRecentAndUninvalidated(t *testing.T) {
	store := newTestStore(t)
	old := time.Now().Add(-time.Hour)
	store.put("cache/pinned", 1000, old)
	store.put("cache/active", 1000, old)
	store.put("cache/recent", 60, time.Now())
	store.put("cache/failing", 60, old)
	store.put("cache/old", 60, old)
	source := &markingSource{
		prefix:  "cache/",
		pinned:  map[string]bool{"cache/pinned": true},
		active:  map[string]bool{"cache/active": true},
		failing: map[string]bool{"cache/failing": true},
	}
	evictor := New(store.client, 100, source)

	report, err := evictor.Evict(context.Background())
	if err != nil {
		t.Fatalf("evict: %v", err)
	}
	// Pinned and active files don't count; of the rest only cache/old can go.
	if report.EvictedUnits != 1 || report.TotalBytes != 120 {
		t.Errorf("report = %+v", report)
	}
	for name, want := range map[string]bool{"cache/pinned": true, "cache/active": true, "cache/recent": true, "cache/failing": true, "cache/old": false} {
		if store.exists(name) != want {
			t.Errorf("%s exists = %v, want %v", name, !want, want)
		}
	}
}

func TestStaleUnitsGoEvenUnderLimit(t *testing.T) {
	store := newTestStore(t)
	store.put("cache/broken", 10, time.Now())
	store.put("cache/fine", 10, time.Now().Add(-time.Hour))
	source := &markingSource{prefix: "cache/", stale: map[string]bool{"cache/broken": true}}
	evictor := New(store.client, 100, source)

	if _, err := evictor.Evict(context.Background()); err != nil {
		t.Fatalf("evict: %v", err)
	}
	if store.exists("cache/broken") || !store.exists("cache/fine") {
		t.Errorf("broken exists = %v, fine exists = %v", store.exists("cache/broken"), store.exists("cache/fine"))
	}
}

func TestNoteWriteSignalsOnlyOverLimit(t *testing.T) {
	store := newTestStore(t)
	evictor := New(store.client, 100, Files("cache/"))

	evictor.NoteWrite("posters/p", 1000)
	evictor.NoteWrite("cache/a", 60)
	if len(evictor.writes) != 0 {
		t.Fatal("signalled under the limit")
	}
	evictor.NoteWrite("cache/b", 60)
	if len(evictor.writes) != 1 {
		t.Fatal("no signal over the limit")
	}
}

func TestRunEvictsAfterWrites(t *testing.T) {
	store := newTestStore(t)
	evictor := New(store.client, 100, Files("cache/"))
	evictor.debounce = 10 * time.Millisecond
	store.client.OnPut(evictor.NoteWrite)
	store.put("cache/old", 80, time.Now().Add(-time.Hour))
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		evictor.Run(ctx)
		close(done)
	}()
	defer func() {
		cancel()
		<-done
	}()

	// The startup pass leaves the cache alone: it is under the limit.
	waitFor(t, func() bool { return evictor.estimate.Load() == 80 })
	if !store.exists("cache/old") {
		t.Fatal("startup pass evicted under the limit")
	}
	store.put("cache/new", 80, time.Now())
	waitFor(t, func() bool { return !store.exists("cache/old") })
	if !store.exists("cache/new") {
		t.Error("recently written blob evicted")
	}
}

// waitFor polls condition until it holds, failing the test after a few seconds.
func waitFor(t *testing.T, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for !condition() {
		if time.Now().After(deadline) {
			t.Fatal("condition not reached")
		}
		time.Sleep(5 * time.Millisecond)
	}
}
