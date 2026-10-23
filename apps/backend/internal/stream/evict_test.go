package stream

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/blobcache"
	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

// testCache is a CacheService over a temporary database and blob directory.
type testCache struct {
	service *CacheService
	root    string
}

// newTestCache opens a CacheService in temporary directories.
func newTestCache(t *testing.T) *testCache {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { database.Close() })
	root := t.TempDir()
	store, err := storage.New(root, "http://backend.test")
	if err != nil {
		t.Fatalf("storage: %v", err)
	}
	return &testCache{service: NewCacheService(cache.New(ctx, database), store), root: root}
}

// ageAll marks every stored blob as last used an hour ago.
func (c *testCache) ageAll(t *testing.T) {
	t.Helper()
	old := time.Now().Add(-time.Hour)
	err := filepath.WalkDir(c.root, func(target string, entry os.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return err
		}
		return os.Chtimes(target, old, old)
	})
	if err != nil {
		t.Fatalf("age: %v", err)
	}
}

// age sets one stream cache blob's last use.
func (c *testCache) age(t *testing.T, relativeKey string, lastUsed time.Time) {
	t.Helper()
	target := filepath.Join(c.root, filepath.FromSlash(blobPrefix+relativeKey))
	if err := os.Chtimes(target, lastUsed, lastUsed); err != nil {
		t.Fatalf("chtimes: %v", err)
	}
}

// units lists the stream cache's eviction units, each as its sorted keys.
func (c *testCache) units(t *testing.T) ([]blobcache.Unit, []string) {
	t.Helper()
	ctx := context.Background()
	files, err := c.service.storage.List(ctx, blobPrefix)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	units, err := c.service.EvictionSource().Units(ctx, files)
	if err != nil {
		t.Fatalf("units: %v", err)
	}
	var described []string
	for _, unit := range units {
		var keys []string
		for _, file := range unit.Files {
			keys = append(keys, strings.TrimPrefix(file.Key, blobPrefix))
		}
		sort.Strings(keys)
		described = append(described, strings.Join(keys, " "))
	}
	return units, described
}

// hlsOrigin serves a master playlist with one variant of two segments.
func hlsOrigin(t *testing.T) *httptest.Server {
	t.Helper()
	mux := http.NewServeMux()
	mux.HandleFunc("/master.m3u8", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nvariant.m3u8\n"))
	})
	mux.HandleFunc("/variant.m3u8", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("#EXTM3U\n#EXTINF:4,\none.ts\n#EXTINF:4,\ntwo.ts\n#EXT-X-ENDLIST\n"))
	})
	mux.HandleFunc("/one.ts", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("segment one"))
	})
	mux.HandleFunc("/two.ts", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("segment two"))
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server
}

func TestHLSStreamIsOneEvictionUnit(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	masterURL := origin.URL + "/master.m3u8"
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	// A segment only the playback proxy wrote stands alone.
	_ = testCache.service.storage.Put(ctx, blobPrefix+"seg/lonely.ts", strings.NewReader("x"), 1, "")

	units, described := testCache.units(t)
	if len(units) != 2 {
		t.Fatalf("units = %q", described)
	}
	stream := units[0]
	if len(stream.Files) != 4 {
		stream = units[1]
	}
	if len(stream.Files) != 4 || stream.Stale || stream.Pinned {
		t.Errorf("units = %q, stream = %+v", described, stream)
	}

	// Evicting it drops every entry, so lookups and the proxy go to the origin.
	testCache.ageAll(t)
	evictor := blobcache.New(testCache.service.storage, 1, testCache.service.EvictionSource())
	report, err := evictor.Evict(ctx)
	if err != nil || report.EvictedUnits != 2 || report.TotalBytes != 0 {
		t.Fatalf("report = %+v, %v", report, err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); ok {
		t.Error("lookup still hits after eviction")
	}
	if testCache.service.segmentCached(ctx, origin.URL+"/one.ts") {
		t.Error("segment flag survived eviction")
	}
	for _, url := range []string{masterURL, origin.URL + "/variant.m3u8"} {
		if _, err := testCache.service.kv.Get(ctx, kvPrefix+urlHash(url)); err != cache.ErrMiss {
			t.Errorf("entry for %s survived eviction", url)
		}
	}
}

func TestDownloadsArePinned(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	blobURL, err := testCache.service.CacheNow(ctx, origin.URL+"/master.m3u8", &plugins.ResolveResult{URL: origin.URL + "/master.m3u8"}, nil, nil)
	if err != nil {
		t.Fatalf("cache: %v", err)
	}
	testCache.service.SetPinnedURLs(func(ctx context.Context) ([]string, error) {
		return []string{blobURL}, nil
	})
	testCache.ageAll(t)

	evictor := blobcache.New(testCache.service.storage, 1, testCache.service.EvictionSource())
	report, err := evictor.Evict(ctx)
	if err != nil || report.EvictedUnits != 0 || report.TotalBytes != 0 {
		t.Fatalf("report = %+v, %v", report, err)
	}
	if _, ok := testCache.service.Lookup(ctx, origin.URL+"/master.m3u8"); !ok {
		t.Error("download evicted")
	}
}

func TestPartiallyEvictedStreamIsStale(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	masterURL := origin.URL + "/master.m3u8"
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	_ = testCache.service.storage.Delete(ctx, segmentKey(origin.URL+"/two.ts"))
	testCache.ageAll(t)

	units, described := testCache.units(t)
	if len(units) != 1 || !units[0].Stale {
		t.Fatalf("units = %q, stale = %v", described, units[0].Stale)
	}
	evictor := blobcache.New(testCache.service.storage, blobcache.DefaultMaxBytes, testCache.service.EvictionSource())
	if report, err := evictor.Evict(ctx); err != nil || report.EvictedUnits != 1 {
		t.Fatalf("report = %+v, %v", report, err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); ok {
		t.Error("lookup hits a stream missing a segment")
	}
}

func TestRunningCacheOperationKeepsItsBlobs(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	store := testCache.service.storage
	end := testCache.service.beginOperation()
	// A long download: started an hour ago, its blobs idle past the grace period.
	testCache.service.operations[0] = time.Now().Add(-time.Hour)
	_ = store.Put(ctx, blobPrefix+"seg/before.ts", strings.NewReader("x"), 1, "")
	_ = store.Put(ctx, blobPrefix+"seg/writing.ts", strings.NewReader("x"), 1, "")
	testCache.age(t, "seg/before.ts", time.Now().Add(-2*time.Hour))
	testCache.age(t, "seg/writing.ts", time.Now().Add(-30*time.Minute))

	units, described := testCache.units(t)
	active := map[string]bool{}
	for index, unit := range units {
		active[described[index]] = unit.Active
	}
	if !active["seg/writing.ts"] || active["seg/before.ts"] {
		t.Errorf("active = %v", active)
	}
	end()
	units, _ = testCache.units(t)
	for _, unit := range units {
		if unit.Active {
			t.Errorf("%s active after the operation ended", unit.Files[0].Key)
		}
	}
}

func TestPlayingStreamIsNotCounted(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	masterURL := origin.URL + "/master.m3u8"
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	_ = testCache.service.storage.Put(ctx, blobPrefix+"seg/other.ts", strings.NewReader("other"), 5, "")
	testCache.ageAll(t)
	// The player just fetched one segment: the whole stream is in playback.
	testCache.age(t, strings.TrimPrefix(segmentKey(origin.URL+"/one.ts"), blobPrefix), time.Now())

	evictor := blobcache.New(testCache.service.storage, 4, testCache.service.EvictionSource())
	report, err := evictor.Evict(ctx)
	if err != nil || report.EvictedUnits != 1 || report.TotalBytes != 0 {
		t.Fatalf("report = %+v, %v", report, err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); !ok {
		t.Fatal("playing stream evicted")
	}

	// Once idle it counts and goes like any other.
	testCache.ageAll(t)
	report, err = evictor.Evict(ctx)
	if err != nil || report.EvictedUnits != 1 || report.TotalBytes != 0 {
		t.Fatalf("idle report = %+v, %v", report, err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); ok {
		t.Error("idle stream kept")
	}
}

func TestLookupMissesWhenBlobIsGone(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	key := blobPrefix + "mp4/" + urlHash("https://cdn.test/v.mp4") + ".mp4"
	_ = testCache.service.storage.Put(ctx, key, strings.NewReader("video"), 5, "")
	if _, err := testCache.service.writeEntry(ctx, "https://cdn.test/v.mp4", key, "video/mp4", time.Hour); err != nil {
		t.Fatalf("entry: %v", err)
	}
	if _, ok := testCache.service.Lookup(ctx, "https://cdn.test/v.mp4"); !ok {
		t.Fatal("lookup misses a cached file")
	}
	_ = testCache.service.storage.Delete(ctx, key)
	if _, ok := testCache.service.Lookup(ctx, "https://cdn.test/v.mp4"); ok {
		t.Error("lookup hits a deleted file")
	}
}

func TestLookupMissesStreamMissingASegment(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	masterURL := origin.URL + "/master.m3u8"
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); !ok {
		t.Fatal("lookup misses a complete stream")
	}
	_ = testCache.service.storage.Delete(ctx, segmentKey(origin.URL+"/two.ts"))
	if _, ok := testCache.service.Lookup(ctx, masterURL); ok {
		t.Fatal("lookup hits a stream missing a segment")
	}
	// Caching again fetches the missing segment and heals the stream.
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("recache: %v", err)
	}
	if _, ok := testCache.service.Lookup(ctx, masterURL); !ok {
		t.Error("recached stream misses")
	}
}

func TestReconcileDropsEntriesForMissingBlobs(t *testing.T) {
	ctx := context.Background()
	testCache := newTestCache(t)
	origin := hlsOrigin(t)
	masterURL := origin.URL + "/master.m3u8"
	if _, err := testCache.service.CacheNow(ctx, masterURL, &plugins.ResolveResult{URL: masterURL}, nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	mp4Key := blobPrefix + "mp4/" + urlHash("https://cdn.test/v.mp4") + ".mp4"
	_ = testCache.service.storage.Put(ctx, mp4Key, strings.NewReader("video"), 5, "")
	_, _ = testCache.service.writeEntry(ctx, "https://cdn.test/v.mp4", mp4Key, "video/mp4", time.Hour)
	_ = testCache.service.storage.Delete(ctx, segmentKey(origin.URL+"/one.ts"))

	removed, err := testCache.service.Reconcile(ctx)
	// The master and variant entries and the deleted segment's flag.
	if err != nil || removed != 3 {
		t.Fatalf("removed = %d, %v", removed, err)
	}
	if _, err := testCache.service.kv.Get(ctx, kvPrefix+urlHash(masterURL)); err != cache.ErrMiss {
		t.Error("entry of an incomplete stream kept")
	}
	if _, err := testCache.service.kv.Get(ctx, kvSegmentPrefix+urlHash(origin.URL+"/two.ts")); err != nil {
		t.Error("flag of a present segment dropped")
	}
	if _, ok := testCache.service.Lookup(ctx, "https://cdn.test/v.mp4"); !ok {
		t.Error("complete mp4 entry dropped")
	}
}

func TestManifestURLHandsOutCachedBlobsDirectly(t *testing.T) {
	testCache := newTestCache(t)
	proxy := NewProxy(testCache.service, testCache.service.kv, "http://backend.test")
	blobURL := testCache.service.storage.DirectURL(blobPrefix + "mp4/abc.mp4")
	playable, err := proxy.ManifestURL(context.Background(), "https://hoster.test/e/abc", &plugins.ResolveResult{URL: blobURL, MimeType: "video/mp4"})
	if err != nil || playable != blobURL {
		t.Errorf("playable = %q, %v", playable, err)
	}
	if _, caching := testCache.service.inflight.Load("https://hoster.test/e/abc"); caching {
		t.Error("started caching a cached blob")
	}
}
