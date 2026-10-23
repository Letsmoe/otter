package stream

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"

	"github.com/neoworks/otter/internal/plugins"
)

// Downloaded is a stream saved for offline playback.
type Downloaded struct {
	URL      string
	MimeType string
}

// Download saves a stream into the blob store for offline playback. Unlike
// playback caching, an HLS master keeps only its best variant. Blob keys
// derive from resumeKey (the stored link, stable across resolves), so a
// download interrupted by a restart continues where it stopped.
func (s *CacheService) Download(ctx context.Context, result *plugins.ResolveResult, resumeKey string, progress func(pct float64, rx, total int64)) (Downloaded, error) {
	defer s.beginOperation()()
	defer s.lockDownload(resumeKey)()
	if !isHLS(result) {
		blobURL, err := s.downloadMP4(ctx, result.URL, result.Headers, resumeKey, progress)
		return Downloaded{URL: blobURL, MimeType: "video/mp4"}, err
	}
	mediaURL, err := s.bestVariant(ctx, result.URL, result.Headers)
	if err != nil {
		return Downloaded{}, err
	}
	blobURL, err := s.downloadHLS(ctx, mediaURL, result.Headers, resumeKey, progress)
	return Downloaded{URL: blobURL, MimeType: "application/x-mpegURL"}, err
}

// lockDownload waits until no other download of resumeKey runs; call the
// returned function when done.
func (s *CacheService) lockDownload(resumeKey string) func() {
	value, _ := s.downloadLocks.LoadOrStore(resumeKey, &sync.Mutex{})
	lock := value.(*sync.Mutex)
	lock.Lock()
	return lock.Unlock
}

// -- MP4 --

func (s *CacheService) downloadMP4(ctx context.Context, mediaURL string, headers map[string]string, resumeKey string, progress func(float64, int64, int64)) (string, error) {
	key := blobPrefix + "mp4/" + urlHash("download:"+resumeKey) + ".mp4"
	resp, offset, err := s.fetchFrom(ctx, mediaURL, headers, s.storage.PartialSize(key))
	if err != nil {
		return "", fmt.Errorf("fetch MP4: %w", err)
	}
	defer resp.Body.Close()

	var reader io.Reader = resp.Body
	if progress != nil && resp.ContentLength > 0 {
		reader = &progressReader{r: resp.Body, rx: offset, total: offset + resp.ContentLength, fn: progress}
	}
	if err := s.storage.PutResumable(ctx, key, reader, offset); err != nil {
		return "", fmt.Errorf("save MP4: %w", err)
	}
	return s.writeEntry(ctx, mediaURL, key, "video/mp4", ttlMP4)
}

// fetchFrom requests mediaURL from byte offset on and returns the offset the
// response actually starts at: 0 when the server ignores the range or
// refuses it.
func (s *CacheService) fetchFrom(ctx context.Context, mediaURL string, headers map[string]string, offset int64) (*http.Response, int64, error) {
	if offset <= 0 {
		resp, err := s.fetch(ctx, mediaURL, headers)
		return resp, 0, err
	}
	rangeHeaders := map[string]string{"Range": fmt.Sprintf("bytes=%d-", offset)}
	for name, value := range headers {
		rangeHeaders[name] = value
	}
	resp, err := s.fetch(ctx, mediaURL, rangeHeaders)
	if err != nil {
		return s.fetchFrom(ctx, mediaURL, headers, 0)
	}
	if startsAt(resp, offset) {
		return resp, offset, nil
	}
	if resp.StatusCode == http.StatusOK {
		return resp, 0, nil
	}
	resp.Body.Close()
	return s.fetchFrom(ctx, mediaURL, headers, 0)
}

// startsAt reports whether resp is a partial response beginning at offset.
func startsAt(resp *http.Response, offset int64) bool {
	if resp.StatusCode != http.StatusPartialContent {
		return false
	}
	return strings.HasPrefix(resp.Header.Get("Content-Range"), fmt.Sprintf("bytes %d-", offset))
}

// -- HLS --

func (s *CacheService) downloadHLS(ctx context.Context, manifestURL string, headers map[string]string, resumeKey string, progress func(float64, int64, int64)) (string, error) {
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
	lines := strings.Split(string(body), "\n")
	segments := mediaSegments(lines, base)
	keys := downloadSegmentKeys(segments, resumeKey)

	if err := s.downloadSegments(ctx, segments, keys, headers, progress); err != nil {
		return "", fmt.Errorf("download segments: %w", err)
	}
	manifest := s.rewriteSegments(lines, keys)
	manifestKey := blobPrefix + "manifest/" + urlHash("download:"+resumeKey) + ".m3u8"
	if err := s.storage.Put(ctx, manifestKey, strings.NewReader(manifest), int64(len(manifest)), "application/x-mpegURL"); err != nil {
		return "", fmt.Errorf("save manifest: %w", err)
	}
	return s.storage.DirectURL(manifestKey), nil
}

// mediaSegments returns the absolute segment URLs of a media playlist, in order.
func mediaSegments(lines []string, base *url.URL) []string {
	var segments []string
	afterExtInf := false
	for _, rawLine := range lines {
		line := strings.TrimSpace(rawLine)
		if afterExtInf && line != "" && !strings.HasPrefix(line, "#") {
			segments = append(segments, absURL(base, line))
			afterExtInf = false
			continue
		}
		if strings.HasPrefix(line, "#EXTINF") {
			afterExtInf = true
		}
	}
	return segments
}

// downloadSegmentKeys names segments by link, segment count and position
// instead of by URL, whose signed tokens change with every resolve.
func downloadSegmentKeys(segments []string, resumeKey string) []string {
	keys := make([]string, len(segments))
	for index, segment := range segments {
		identity := fmt.Sprintf("download:%s#%d/%d", resumeKey, index, len(segments))
		keys[index] = blobPrefix + "seg/" + urlHash(identity) + segExt(segment)
	}
	return keys
}

// rewriteSegments replaces each segment line with its blob URL, in order.
func (s *CacheService) rewriteSegments(lines []string, keys []string) string {
	out := make([]string, 0, len(lines))
	next := 0
	afterExtInf := false
	for _, rawLine := range lines {
		line := strings.TrimSpace(rawLine)
		if afterExtInf && line != "" && !strings.HasPrefix(line, "#") && next < len(keys) {
			out = append(out, s.storage.DirectURL(keys[next]))
			next++
			afterExtInf = false
			continue
		}
		if strings.HasPrefix(line, "#EXTINF") {
			afterExtInf = true
		}
		out = append(out, line)
	}
	return strings.Join(out, "\n")
}

// segmentProgress counts finished segments and their bytes across workers.
type segmentProgress struct {
	mutex    sync.Mutex
	done     int
	received int64
	total    int
	report   func(float64, int64, int64)
}

func (p *segmentProgress) add(size int64) {
	p.mutex.Lock()
	defer p.mutex.Unlock()
	p.done++
	p.received += size
	if p.report != nil {
		p.report(float64(p.done)/float64(p.total)*100, p.received, 0)
	}
}

// downloadSegments stores every segment not stored yet; ones kept from an
// interrupted run count as done right away.
func (s *CacheService) downloadSegments(ctx context.Context, segments, keys []string, headers map[string]string, progress func(float64, int64, int64)) error {
	tracker := &segmentProgress{total: len(segments), report: progress}
	var (
		workers     sync.WaitGroup
		errorsMutex sync.Mutex
		failures    []error
		slots       = make(chan struct{}, segWorkers)
	)
	for index, segment := range segments {
		if s.storage.Touch(ctx, keys[index]) == nil {
			tracker.add(s.storage.Size(keys[index]))
			continue
		}
		workers.Add(1)
		slots <- struct{}{}
		go func(segmentURL, key string) {
			defer workers.Done()
			defer func() { <-slots }()
			size, err := s.downloadSegment(ctx, segmentURL, key, headers)
			if err != nil {
				errorsMutex.Lock()
				failures = append(failures, err)
				errorsMutex.Unlock()
				return
			}
			tracker.add(size)
		}(segment, keys[index])
	}
	workers.Wait()
	if len(failures) > 0 {
		return fmt.Errorf("%d/%d segments failed (first: %w)", len(failures), len(segments), failures[0])
	}
	return nil
}

func (s *CacheService) downloadSegment(ctx context.Context, segmentURL, key string, headers map[string]string) (int64, error) {
	resp, err := s.fetch(ctx, segmentURL, headers)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if err := s.storage.Put(ctx, key, resp.Body, resp.ContentLength, "video/MP2T"); err != nil {
		return 0, fmt.Errorf("save segment: %w", err)
	}
	return s.storage.Size(key), nil
}

// bestVariant returns the highest-bandwidth variant of a master playlist, or
// the URL itself when it is already a media playlist.
func (s *CacheService) bestVariant(ctx context.Context, manifestURL string, headers map[string]string) (string, error) {
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
	best := highestBandwidthVariant(string(body), base)
	if best == "" {
		return manifestURL, nil
	}
	return best, nil
}

// highestBandwidthVariant picks the variant with the largest BANDWIDTH; "" for
// a media playlist.
func highestBandwidthVariant(manifest string, base *url.URL) string {
	best := ""
	bestBandwidth := -1
	pendingBandwidth := -1
	for _, rawLine := range strings.Split(manifest, "\n") {
		line := strings.TrimSpace(rawLine)
		if strings.HasPrefix(line, "#EXT-X-STREAM-INF") {
			pendingBandwidth = bandwidthOf(line)
			continue
		}
		if pendingBandwidth < 0 || line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if pendingBandwidth > bestBandwidth {
			best = absURL(base, line)
			bestBandwidth = pendingBandwidth
		}
		pendingBandwidth = -1
	}
	return best
}

// bandwidthOf reads BANDWIDTH from an #EXT-X-STREAM-INF line; 0 when missing.
func bandwidthOf(line string) int {
	for _, attribute := range strings.Split(line, ",") {
		name, value, found := strings.Cut(attribute, "=")
		if !found || !strings.HasSuffix(strings.TrimSpace(name), "BANDWIDTH") || strings.Contains(name, "AVERAGE") {
			continue
		}
		bandwidth, err := strconv.Atoi(strings.TrimSpace(value))
		if err == nil {
			return bandwidth
		}
	}
	return 0
}
