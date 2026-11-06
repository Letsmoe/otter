package stream

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/Letsmoe/otter/internal/plugins"
)

// Probing checks that a resolved stream really delivers video before anyone
// plays it, and measures how fast: a resolver returning a URL says nothing
// about whether the playlist answers or the segments are alive. Everything
// goes through the same upstream path (VPN gate, headers) as playback.

// probeBytes is how much media one probe downloads: enough for a meaningful
// speed, little enough to finish within a second on a usable link.
const probeBytes = 512 * 1024

// Hoster pages carry placeholder and ad clips (bigwarp: an 8 s, 1 MB MP4)
// that a generic page scan takes for the video. Nothing below these is a
// movie or an episode.
const (
	minimumFileBytes       = 20 * 1024 * 1024
	minimumDurationSeconds = 60.0
)

// ErrNotVideo means the stream answered, but not with video (an HTML error
// page, an empty playlist, a dead segment, a clip too short to be the title).
var ErrNotVideo = errors.New("the stream doesn't deliver video")

// ProbeResult is what a successful probe measured.
type ProbeResult struct {
	// BytesPerSecond of the media download; 0 when only a manifest was checked.
	BytesPerSecond float64
	// Height of the best resolution an HLS stream offers, e.g. 1080; 0 when unknown.
	Height int
}

// Quality is the height as a label, "1080p"; "" when unknown.
func (r ProbeResult) Quality() string {
	if r.Height <= 0 {
		return ""
	}
	return strconv.Itoa(r.Height) + "p"
}

// Probe downloads the start of the media behind a resolved stream and checks
// it is video: for HLS the first segment of the variant the TV stream would
// use, for files their first bytes. DASH is only checked for a manifest.
func (p *Proxy) Probe(ctx context.Context, result *plugins.ResolveResult) (ProbeResult, error) {
	mimeType := strings.ToLower(result.MimeType)
	if strings.Contains(mimeType, "mpegurl") {
		return p.probeHLS(ctx, result.URL, result.Headers)
	}
	if strings.Contains(mimeType, "dash") {
		return p.probeDASH(ctx, result.URL, result.Headers)
	}
	return p.probeFile(ctx, result.URL, result.Headers)
}

func (p *Proxy) probeHLS(ctx context.Context, manifestURL string, headers map[string]string) (ProbeResult, error) {
	playlist, err := p.loadMediaPlaylist(ctx, manifestURL, headers, noHeightLimit)
	if err != nil {
		return ProbeResult{}, err
	}
	if len(playlist.segments) == 0 {
		return ProbeResult{}, fmt.Errorf("%w: the playlist has no segments", ErrNotVideo)
	}
	if duration := playlist.totalDuration(); duration < minimumDurationSeconds {
		return ProbeResult{}, fmt.Errorf("%w: the stream is only %.0f s long", ErrNotVideo, duration)
	}
	segment := playlist.segments[0]
	started := time.Now()
	body, err := p.readSegmentStart(ctx, segment, headers)
	if err != nil {
		return ProbeResult{}, err
	}
	elapsed := time.Since(started)
	// Encrypted segments can't be checked without their key; their arrival is enough.
	if segment.key == nil && !looksLikeSegment(body, playlist.initURL != "") {
		return ProbeResult{}, fmt.Errorf("%w: the first segment isn't MPEG-TS or fMP4", ErrNotVideo)
	}
	return ProbeResult{BytesPerSecond: bytesPerSecond(len(body), elapsed), Height: playlist.height}, nil
}

// readSegmentStart reads up to probeBytes of a segment; local copies count
// as instantly available.
func (p *Proxy) readSegmentStart(ctx context.Context, segment mediaSegment, headers map[string]string) ([]byte, error) {
	if segment.byteRange != nil {
		return p.readSegment(ctx, segment, headers)
	}
	if local, ok := p.readLocalBlob(ctx, segment.url); ok {
		return local, nil
	}
	return p.readStart(ctx, segment.url, headers)
}

func (p *Proxy) probeFile(ctx context.Context, fileURL string, headers map[string]string) (ProbeResult, error) {
	if local, ok := p.readLocalBlob(ctx, fileURL); ok {
		return ProbeResult{BytesPerSecond: bytesPerSecond(len(local), 0)}, nil
	}
	started := time.Now()
	body, totalBytes, err := p.readFileStart(ctx, fileURL, headers)
	if err != nil {
		return ProbeResult{}, err
	}
	if !looksLikeFile(body) {
		return ProbeResult{}, fmt.Errorf("%w: the file isn't MP4, WebM or MKV", ErrNotVideo)
	}
	if totalBytes > 0 && totalBytes < minimumFileBytes {
		return ProbeResult{}, fmt.Errorf("%w: the file is only %d KB", ErrNotVideo, totalBytes/1024)
	}
	return ProbeResult{BytesPerSecond: bytesPerSecond(len(body), time.Since(started))}, nil
}

// readFileStart downloads the first probeBytes of a file and reads its
// total size from the response; the size is 0 when the server doesn't say.
func (p *Proxy) readFileStart(ctx context.Context, rawURL string, headers map[string]string) ([]byte, int64, error) {
	rangeHeaders := withRange(headers, &byteRange{offset: 0, length: probeBytes})
	response, err := p.cache.fetch(ctx, rawURL, rangeHeaders)
	if err != nil {
		return nil, 0, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, probeBytes))
	return body, totalSizeOf(response), err
}

// totalSizeOf reads the full size from "Content-Range: bytes 0-524287/<size>",
// or from Content-Length when the server ignored the range.
func totalSizeOf(response *http.Response) int64 {
	if response.StatusCode != http.StatusPartialContent {
		return max(response.ContentLength, 0)
	}
	_, sizeText, found := strings.Cut(response.Header.Get("Content-Range"), "/")
	size, err := strconv.ParseInt(sizeText, 10, 64)
	if !found || err != nil {
		return 0
	}
	return size
}

func (p *Proxy) probeDASH(ctx context.Context, manifestURL string, headers map[string]string) (ProbeResult, error) {
	body, err := p.readStart(ctx, manifestURL, headers)
	if err != nil {
		return ProbeResult{}, err
	}
	if !bytes.Contains(body, []byte("<MPD")) {
		return ProbeResult{}, fmt.Errorf("%w: no DASH manifest", ErrNotVideo)
	}
	return ProbeResult{}, nil
}

// readStart downloads the first probeBytes of a URL. Servers that ignore the
// Range header are cut off after probeBytes as well.
func (p *Proxy) readStart(ctx context.Context, rawURL string, headers map[string]string) ([]byte, error) {
	body, _, err := p.readFileStart(ctx, rawURL, headers)
	return body, err
}

// looksLikeSegment recognizes MPEG-TS (sync byte every 188 bytes) and fMP4
// fragments. Some hosts disguise segments as .html or .png files, so the
// bytes decide, not the name or the Content-Type.
func looksLikeSegment(body []byte, fragmentedMP4 bool) bool {
	if looksLikeTransportStream(body) {
		return true
	}
	if fragmentedMP4 {
		return hasMP4Box(body)
	}
	return false
}

func looksLikeTransportStream(body []byte) bool {
	start := bytes.IndexByte(body, 0x47)
	// Disguised segments may carry a short fake header before the stream.
	if start < 0 || start > 1024 || len(body) < start+3*188 {
		return false
	}
	return body[start+188] == 0x47 && body[start+2*188] == 0x47
}

func looksLikeFile(body []byte) bool {
	if hasMP4Box(body) {
		return true
	}
	ebmlHeader := []byte{0x1a, 0x45, 0xdf, 0xa3}
	return bytes.HasPrefix(body, ebmlHeader)
}

// hasMP4Box checks that the data starts with an ISO BMFF box MP4 files and
// fragments begin with.
func hasMP4Box(body []byte) bool {
	if len(body) < 8 {
		return false
	}
	switch string(body[4:8]) {
	case "ftyp", "styp", "moof", "moov", "sidx", "free", "skip", "mdat", "wide", "emsg":
		return true
	}
	return false
}

// bytesPerSecond treats instant answers (local copies) as 1 ms long.
func bytesPerSecond(size int, elapsed time.Duration) float64 {
	if elapsed < time.Millisecond {
		elapsed = time.Millisecond
	}
	return float64(size) / elapsed.Seconds()
}
