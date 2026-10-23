// Package stream provides Blob-store-backed caching for resolved HLS and MP4 streams.
// The kv cache stores metadata; the blob store holds the actual bytes.
//
// HLS: the manifest is rewritten so all segment URIs point to the blob store, making
// subsequent playback fully local (NAS-speed). Master playlists recurse into
// each quality variant.
//
// MP4: the file is streamed directly from the source into the blob store.
//
// The "inflight" sync.Map deduplicates concurrent background caches for the
// same URL so only one goroutine downloads a given stream at a time.
package stream

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

const (
	ttlManifest = 12 * time.Hour
	ttlMP4      = 7 * 24 * time.Hour
	ttlSegment  = 48 * time.Hour
	segWorkers  = 6 // concurrent segment downloads

	blobPrefix      = "stream-cache/"
	kvPrefix        = "stream:v1:"
	kvSegmentPrefix = "stream:seg:v1:"
)

// cacheEntry is JSON-serialised in the kv cache.
type cacheEntry struct {
	Key      string `json:"key"` // blob key
	MimeType string `json:"mimeType"`
	Expires  int64  `json:"expires"` // unix seconds
}

// CacheService caches resolved streams to the blob store and tracks them in the kv cache.
type CacheService struct {
	kv       *cache.Client
	storage  *storage.Client
	inflight sync.Map // set of stream links currently being cached
	// progressive holds the MP4 downloads running right now by cache ID, so
	// players read them while they grow.
	progressive sync.Map
	// downloadLocks holds a *sync.Mutex per resume key, so two downloads of
	// one link never write the same partial file.
	downloadLocks sync.Map

	// operations holds the start time of every running cache operation, so
	// eviction keeps the blobs they write or reuse.
	operationsMutex sync.Mutex
	operations      map[int]time.Time
	nextOperation   int

	// pinnedURLs lists blob URLs that are user content (downloads).
	pinnedURLs func(ctx context.Context) ([]string, error)

	// upstreamAllowed is asked before every CDN request; nil allows all.
	upstreamAllowed func() error
}

// SetUpstreamGate makes every CDN request ask allowed first, so a VPN that
// drops mid-stream stops playback instead of falling back to the plain line.
func (s *CacheService) SetUpstreamGate(allowed func() error) {
	s.upstreamAllowed = allowed
}

func (s *CacheService) checkUpstream() error {
	if s.upstreamAllowed == nil {
		return nil
	}
	return s.upstreamAllowed()
}

func NewCacheService(kv *cache.Client, store *storage.Client) *CacheService {
	return &CacheService{kv: kv, storage: store}
}

const (
	streamActiveKey = "stream:active"
	// Kept slightly longer than a playing player's segment cadence so brief gaps
	// between requests don't read as "stopped".
	streamActiveTTL = 20 * time.Second
)

// MarkStreamActivity records that a stream is being served right now. Called by
// the playback proxy on each manifest/segment request.
func (s *CacheService) MarkStreamActivity(ctx context.Context) {
	if s.kv == nil {
		return
	}
	_ = s.kv.Set(ctx, streamActiveKey, "1", streamActiveTTL)
}

// StreamActive reports whether a stream was served within the activity window.
func (s *CacheService) StreamActive(ctx context.Context) bool {
	if s.kv == nil {
		return false
	}
	value, err := s.kv.Get(ctx, streamActiveKey)
	return err == nil && value != ""
}

func urlHash(u string) string {
	h := sha256.Sum256([]byte(u))
	return hex.EncodeToString(h[:])[:16]
}

// cacheID names a stream's cache entry and blobs. It derives from the stored
// link, not the resolved URL, whose signed tokens change with every resolve.
func cacheID(sourceURL string) string {
	return urlHash(sourceURL)
}

// Lookup returns a cached ResolveResult pointing to the blob store, or (nil, false).
func (s *CacheService) Lookup(ctx context.Context, sourceURL string) (*plugins.ResolveResult, bool) {
	return s.lookupID(ctx, cacheID(sourceURL))
}

func (s *CacheService) lookupID(ctx context.Context, id string) (*plugins.ResolveResult, bool) {
	val, err := s.kv.Get(ctx, kvPrefix+id)
	if err != nil {
		return nil, false
	}
	var e cacheEntry
	if err := json.Unmarshal([]byte(val), &e); err != nil {
		return nil, false
	}
	if time.Now().Unix() > e.Expires {
		_ = s.kv.Del(ctx, kvPrefix+id)
		return nil, false
	}
	// A blob deleted behind the entry's back means the origin must serve it.
	if !s.blobComplete(ctx, e.Key) {
		_ = s.kv.Del(ctx, kvPrefix+id)
		return nil, false
	}
	_ = s.storage.Touch(ctx, e.Key)
	return &plugins.ResolveResult{URL: s.storage.DirectURL(e.Key), MimeType: e.MimeType}, true
}

// StartCaching begins caching the stream behind sourceURL (the stored link)
// in the background; no-ops if that link is already being cached, whichever
// resolve of it started.
func (s *CacheService) StartCaching(sourceURL string, result *plugins.ResolveResult, headers map[string]string) {
	if _, loaded := s.inflight.LoadOrStore(sourceURL, struct{}{}); loaded {
		return
	}
	go func() {
		defer s.inflight.Delete(sourceURL)
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Hour)
		defer cancel()
		if _, err := s.cache(ctx, sourceURL, result, headers, nil); err != nil {
			log.Printf("stream cache (bg): %s: %v", result.URL, err)
		}
	}()
}

// CacheNow caches a stream synchronously, reporting progress via the optional callback.
// Returns the blob URL of the cached content.
func (s *CacheService) CacheNow(
	ctx context.Context,
	sourceURL string,
	result *plugins.ResolveResult,
	headers map[string]string,
	progress func(pct float64, rx, total int64),
) (string, error) {
	return s.cache(ctx, sourceURL, result, headers, progress)
}

func (s *CacheService) cache(
	ctx context.Context,
	sourceURL string,
	result *plugins.ResolveResult,
	headers map[string]string,
	progress func(float64, int64, int64),
) (string, error) {
	if cached, ok := s.Lookup(ctx, sourceURL); ok {
		return cached.URL, nil
	}
	defer s.beginOperation()()
	if isHLS(result) {
		return s.cacheHLS(ctx, result.URL, sourceURL, headers, progress)
	}
	return s.cacheMP4(ctx, sourceURL, result.URL, headers, progress)
}

// -- HTTP --

// httpClient has connection/header timeouts but no body-read timeout.
// Body reads are bounded by the caller's context (job ctx or 2-hour background ctx).
// A Client.Timeout that covers the body read would kill large MP4 downloads.
var httpClient = &http.Client{
	Transport: &http.Transport{
		DialContext:           (&net.Dialer{Timeout: 30 * time.Second}).DialContext,
		TLSHandshakeTimeout:   15 * time.Second,
		ResponseHeaderTimeout: 30 * time.Second,
		DisableCompression:    true, // don't deflate streams
	},
	CheckRedirect: keepOriginalReferer,
}

// keepOriginalReferer prevents Go's automatic per-hop Referer injection on
// redirects. CDNs with referer whitelists (e.g. txxx's ahcdn: "none,.txxx.com")
// return 403 when the intermediate redirect host leaks in as Referer. The
// caller's explicit Referer, if any, is carried through every hop instead.
func keepOriginalReferer(req *http.Request, via []*http.Request) error {
	if len(via) >= 10 {
		return fmt.Errorf("stopped after 10 redirects")
	}
	originalReferer := via[0].Header.Get("Referer")
	if originalReferer == "" {
		req.Header.Del("Referer")
		return nil
	}
	req.Header.Set("Referer", originalReferer)
	return nil
}

func (s *CacheService) fetch(ctx context.Context, rawURL string, headers map[string]string) (*http.Response, error) {
	if err := s.checkUpstream(); err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	// 206 answers a Range header (byte-range HLS segments).
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		resp.Body.Close()
		return nil, fmt.Errorf("HTTP %d fetching %s", resp.StatusCode, rawURL)
	}
	return resp, nil
}

// -- HLS --

// cacheHLS caches the playlist at manifestURL under entryURL's entry.
func (s *CacheService) cacheHLS(ctx context.Context, manifestURL, entryURL string, headers map[string]string, progress func(float64, int64, int64)) (string, error) {
	resp, err := s.fetch(ctx, manifestURL, headers)
	if err != nil {
		return "", fmt.Errorf("fetch manifest %s: %w", manifestURL, err)
	}
	body, err := io.ReadAll(resp.Body)
	resp.Body.Close()
	if err != nil {
		return "", fmt.Errorf("read manifest: %w", err)
	}

	base, _ := url.Parse(manifestURL)
	content := string(body)

	if strings.Contains(content, "#EXT-X-STREAM-INF") {
		return s.cacheMasterHLS(ctx, content, base, entryURL, headers, progress)
	}
	return s.cacheMediaHLS(ctx, content, base, entryURL, headers, progress)
}

func (s *CacheService) cacheMasterHLS(ctx context.Context, manifest string, base *url.URL, originalURL string, headers map[string]string, progress func(float64, int64, int64)) (string, error) {
	lines := strings.Split(manifest, "\n")

	// Collect variant URLs
	var variantURLs []string
	prevStreamInf := false
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if prevStreamInf && line != "" && !strings.HasPrefix(line, "#") {
			variantURLs = append(variantURLs, absURL(base, line))
		}
		prevStreamInf = strings.HasPrefix(line, "#EXT-X-STREAM-INF")
	}

	// Cache variants concurrently
	var (
		wg     sync.WaitGroup
		mu     sync.Mutex
		cached = make(map[string]string, len(variantURLs))
		sem    = make(chan struct{}, segWorkers)
	)
	for _, vURL := range variantURLs {
		wg.Add(1)
		sem <- struct{}{}
		go func(u string) {
			defer wg.Done()
			defer func() { <-sem }()
			blobURL, err := s.cacheHLS(ctx, u, u, headers, nil)
			mu.Lock()
			if err != nil {
				log.Printf("stream cache: variant %s: %v", u, err)
				cached[u] = u // keep original on failure
			} else {
				cached[u] = blobURL
			}
			mu.Unlock()
		}(vURL)
	}
	wg.Wait()

	// Rewrite master manifest
	out := make([]string, 0, len(lines))
	prevStreamInf = false
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if prevStreamInf && line != "" && !strings.HasPrefix(line, "#") {
			abs := absURL(base, line)
			if u, ok := cached[abs]; ok {
				out = append(out, u)
			} else {
				out = append(out, abs)
			}
			prevStreamInf = false
			continue
		}
		prevStreamInf = strings.HasPrefix(line, "#EXT-X-STREAM-INF")
		out = append(out, line)
	}

	return s.uploadManifest(ctx, originalURL, strings.Join(out, "\n"), ttlManifest)
}

func (s *CacheService) cacheMediaHLS(ctx context.Context, manifest string, base *url.URL, originalURL string, headers map[string]string, progress func(float64, int64, int64)) (string, error) {
	lines := strings.Split(manifest, "\n")

	// Collect segment URLs
	var segments []string
	prevExtInf := false
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if prevExtInf && line != "" && !strings.HasPrefix(line, "#") {
			segments = append(segments, absURL(base, line))
		}
		prevExtInf = strings.HasPrefix(line, "#EXTINF")
	}

	if err := s.cacheSegments(ctx, segments, headers, progress); err != nil {
		return "", fmt.Errorf("download segments: %w", err)
	}

	// Rewrite manifest with blob segment URLs
	out := make([]string, 0, len(lines))
	prevExtInf = false
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if prevExtInf && line != "" && !strings.HasPrefix(line, "#") {
			abs := absURL(base, line)
			out = append(out, s.storage.DirectURL(segmentKey(abs)))
			prevExtInf = false
			continue
		}
		prevExtInf = strings.HasPrefix(line, "#EXTINF")
		out = append(out, line)
	}

	return s.uploadManifest(ctx, originalURL, strings.Join(out, "\n"), ttlManifest)
}

func (s *CacheService) cacheSegments(ctx context.Context, segments []string, headers map[string]string, progress func(float64, int64, int64)) error {
	total := len(segments)
	if total == 0 {
		return nil
	}

	var (
		done int
		rx   int64
		mu   sync.Mutex
		wg   sync.WaitGroup
		sem  = make(chan struct{}, segWorkers)
		errs []error
	)

	for _, seg := range segments {
		// Skip already-cached segments
		if s.segmentCached(ctx, seg) {
			mu.Lock()
			done++
			mu.Unlock()
			continue
		}

		wg.Add(1)
		sem <- struct{}{}
		go func(u string) {
			defer wg.Done()
			defer func() { <-sem }()

			n, err := s.cacheSegment(ctx, u, headers)
			mu.Lock()
			defer mu.Unlock()
			if err != nil {
				log.Printf("stream cache: segment %s: %v", u, err)
				errs = append(errs, err)
			} else {
				done++
				rx += n
				if progress != nil {
					pct := float64(done) / float64(total) * 100
					progress(pct, rx, 0) // total bytes unknown for HLS
				}
			}
		}(seg)
	}

	wg.Wait()
	if len(errs) > 0 {
		return fmt.Errorf("%d/%d segments failed (first: %w)", len(errs), total, errs[0])
	}
	return nil
}

// segmentCached reports whether a segment is already in the blob store. It
// marks the blob used, so eviction keeps it for the manifest being built.
func (s *CacheService) segmentCached(ctx context.Context, segURL string) bool {
	if value, _ := s.kv.Get(ctx, kvSegmentPrefix+urlHash(segURL)); value != "1" {
		return false
	}
	return s.storage.Touch(ctx, segmentKey(segURL)) == nil
}

func (s *CacheService) cacheSegment(ctx context.Context, segURL string, headers map[string]string) (int64, error) {
	key := segmentKey(segURL)

	resp, err := s.fetch(ctx, segURL, headers)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	ct := resp.Header.Get("Content-Type")
	if ct == "" {
		ct = "video/MP2T"
	}
	size := resp.ContentLength

	if err := s.storage.Put(ctx, key, resp.Body, size, ct); err != nil {
		return 0, fmt.Errorf("upload segment: %w", err)
	}
	_ = s.kv.Set(ctx, kvSegmentPrefix+urlHash(segURL), "1", ttlSegment)
	return max64(size, 0), nil
}

// -- MP4 --

// cacheMP4 downloads the file into a resumable partial blob that players
// read while it grows; an interrupted download continues from where it
// stopped, with whatever URL the link resolves to next.
func (s *CacheService) cacheMP4(ctx context.Context, sourceURL, mediaURL string, headers map[string]string, progress func(float64, int64, int64)) (string, error) {
	id := cacheID(sourceURL)
	key := blobPrefix + "mp4/" + id + ".mp4"
	resp, offset, err := s.fetchFrom(ctx, mediaURL, headers, s.storage.PartialSize(key))
	if err != nil {
		return "", fmt.Errorf("fetch MP4: %w", err)
	}
	defer resp.Body.Close()

	var reader io.Reader = resp.Body
	if progress != nil && resp.ContentLength > 0 {
		reader = &progressReader{r: resp.Body, rx: offset, total: offset + resp.ContentLength, fn: progress}
	}
	file := s.beginProgressive(id, key, offset, resp.ContentLength)
	// Deferred, so the cache entry exists before the file stops being progressive.
	defer s.endProgressive(id, file)
	if err := s.storage.PutResumableObserved(ctx, key, reader, offset, file.observer()); err != nil {
		return "", fmt.Errorf("save MP4: %w", err)
	}
	return s.writeEntry(ctx, sourceURL, key, "video/mp4", ttlMP4)
}

// -- manifest upload --

func (s *CacheService) uploadManifest(ctx context.Context, originalURL, content string, ttl time.Duration) (string, error) {
	key := blobPrefix + "manifest/" + urlHash(originalURL) + ".m3u8"
	r := strings.NewReader(content)
	if err := s.storage.Put(ctx, key, r, int64(len(content)), "application/x-mpegURL"); err != nil {
		return "", fmt.Errorf("upload manifest: %w", err)
	}
	return s.writeEntry(ctx, originalURL, key, "application/x-mpegURL", ttl)
}

func (s *CacheService) writeEntry(ctx context.Context, originalURL, key, mimeType string, ttl time.Duration) (string, error) {
	e := cacheEntry{Key: key, MimeType: mimeType, Expires: time.Now().Add(ttl).Unix()}
	data, _ := json.Marshal(e)
	if err := s.kv.Set(ctx, kvPrefix+urlHash(originalURL), string(data), ttl); err != nil {
		return "", err
	}
	return s.storage.DirectURL(key), nil
}

// -- helpers --

func isHLS(r *plugins.ResolveResult) bool {
	return strings.Contains(r.MimeType, "mpegURL") ||
		strings.Contains(r.MimeType, "dash+xml") ||
		strings.HasSuffix(strings.SplitN(r.URL, "?", 2)[0], ".m3u8")
}

func absURL(base *url.URL, ref string) string {
	if strings.HasPrefix(ref, "http://") || strings.HasPrefix(ref, "https://") {
		return ref
	}
	if base == nil {
		return ref
	}
	u, err := base.Parse(ref)
	if err != nil {
		return ref
	}
	return u.String()
}

// segmentKey is the blob key an HLS segment is cached under.
func segmentKey(segURL string) string {
	return blobPrefix + "seg/" + urlHash(segURL) + segExt(segURL)
}

func segExt(segURL string) string {
	path := strings.SplitN(segURL, "?", 2)[0]
	switch {
	case strings.HasSuffix(path, ".aac"):
		return ".aac"
	case strings.HasSuffix(path, ".m4s"):
		return ".m4s"
	case strings.HasSuffix(path, ".mp4"):
		return ".mp4"
	default:
		return ".ts"
	}
}

func max64(a, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

type progressReader struct {
	r     io.Reader
	total int64
	rx    int64
	fn    func(float64, int64, int64)
}

func (p *progressReader) Read(b []byte) (int, error) {
	n, err := p.r.Read(b)
	p.rx += int64(n)
	if p.fn != nil {
		pct := float64(p.rx) / float64(p.total) * 100
		p.fn(pct, p.rx, p.total)
	}
	return n, err
}
