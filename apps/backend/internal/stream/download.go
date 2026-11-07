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

	"github.com/Letsmoe/otter/internal/plugins"
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

// -- HLS (download_hls.go) --

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

// bestVariant returns the highest-bandwidth variant of a master playlist, or
// the URL itself when it is already a media playlist.
func (s *CacheService) bestVariant(ctx context.Context, manifestURL string, headers map[string]string) (string, error) {
	content, err := s.fetchManifest(ctx, manifestURL, headers)
	if err != nil {
		return "", err
	}
	base, _ := url.Parse(manifestURL)
	best := highestBandwidthVariant(content, base)
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
