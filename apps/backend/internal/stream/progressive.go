package stream

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Progressive MP4s: the playback cache download of a link is read by the
// players while it is still running, so the phone and a TV playing from it
// share one connection to the CDN. A read that starts far ahead of the
// download (a seek, the index at the end of the file) and a read the
// download stops feeding are relayed from the CDN instead.

const (
	// progressiveLead is how far ahead of the download a read may start and
	// still wait for it instead of being relayed.
	progressiveLead  = 2 << 20
	progressiveChunk = 256 << 10
)

// progressiveStall is how long a read waits for new bytes before it relays
// the rest of its range.
var progressiveStall = 8 * time.Second

// progressiveFile is an MP4 download in progress.
type progressiveFile struct {
	key         string
	partialPath string
	total       int64

	mutex sync.Mutex
	size  int64
	ended bool
	// changed is closed and replaced whenever size or ended change.
	changed chan struct{}
}

// beginProgressive registers a download of total bytes that starts with size
// bytes already stored. nil when the total is unknown: ranges can't be
// answered without it.
func (s *CacheService) beginProgressive(id, key string, size, contentLength int64) *progressiveFile {
	if contentLength <= 0 {
		return nil
	}
	partialPath, err := s.storage.PartialPath(key)
	if err != nil {
		return nil
	}
	file := &progressiveFile{key: key, partialPath: partialPath, total: size + contentLength, size: size, changed: make(chan struct{})}
	s.progressive.Store(id, file)
	return file
}

// endProgressive unregisters a download; waiting readers relay what is missing.
func (s *CacheService) endProgressive(id string, file *progressiveFile) {
	if file == nil {
		return
	}
	s.progressive.CompareAndDelete(id, file)
	file.mutex.Lock()
	defer file.mutex.Unlock()
	file.ended = true
	file.notify()
}

func (s *CacheService) progressiveFile(id string) (*progressiveFile, bool) {
	value, ok := s.progressive.Load(id)
	if !ok {
		return nil, false
	}
	return value.(*progressiveFile), true
}

// observer returns the callback the download reports its size to; nil for no file.
func (file *progressiveFile) observer() func(size int64) {
	if file == nil {
		return nil
	}
	return file.grow
}

func (file *progressiveFile) grow(size int64) {
	file.mutex.Lock()
	defer file.mutex.Unlock()
	file.size = size
	file.notify()
}

// notify wakes every waiting reader; the caller holds the mutex.
func (file *progressiveFile) notify() {
	close(file.changed)
	file.changed = make(chan struct{})
}

func (file *progressiveFile) state() (size int64, ended bool, changed <-chan struct{}) {
	file.mutex.Lock()
	defer file.mutex.Unlock()
	return file.size, file.ended, file.changed
}

func (file *progressiveFile) storedSize() int64 {
	size, _, _ := file.state()
	return size
}

// waitBeyond blocks until more than offset bytes are stored. It returns the
// stored size, or an error once the download ended or stalled before that.
func (file *progressiveFile) waitBeyond(ctx context.Context, offset int64) (int64, error) {
	for {
		size, ended, changed := file.state()
		if size > offset {
			return size, nil
		}
		if ended {
			return size, errors.New("the download ended")
		}
		select {
		case <-changed:
		case <-time.After(progressiveStall):
			return size, errors.New("the download stalled")
		case <-ctx.Done():
			return size, ctx.Err()
		}
	}
}

// open opens the stored bytes: the partial file, or the finished blob once
// the download renamed it.
func (file *progressiveFile) open(s *CacheService) (*os.File, error) {
	reader, err := os.Open(file.partialPath)
	if err == nil {
		return reader, nil
	}
	finishedPath, pathErr := s.storage.Path(file.key)
	if pathErr != nil {
		return nil, pathErr
	}
	return os.Open(finishedPath)
}

// -- serving --

// mp4Upstream is where the relay fetches what the download can't serve.
type mp4Upstream struct {
	url     string
	headers map[string]string
}

// byteSpan is an inclusive byte range of a file.
type byteSpan struct {
	start int64
	end   int64
}

// serveProgressive answers a player from a growing download. It returns
// false, having written nothing, when the request must be relayed instead.
func (p *Proxy) serveProgressive(w http.ResponseWriter, r *http.Request, file *progressiveFile, upstream mp4Upstream) bool {
	rangeHeader := r.Header.Get("Range")
	span, ok := requestedSpan(rangeHeader, file.total)
	if !ok || span.start > file.storedSize()+progressiveLead {
		return false
	}
	reader, err := file.open(p.cache)
	if err != nil {
		return false
	}
	defer reader.Close()
	writeSpanHeaders(w, span, file.total, rangeHeader != "")
	if r.Method == http.MethodHead {
		return true
	}
	offset, err := copyProgressive(r.Context(), w, reader, file, span)
	if errors.Is(err, errDownloadBehind) && r.Context().Err() == nil {
		// The download ended or stalled: the rest of the range comes from the
		// CDN. Headers are out, so a failed relay just ends the body early and
		// the player reconnects.
		_ = p.relaySpan(r.Context(), w, upstream, byteSpan{start: offset, end: span.end})
	}
	return true
}

var errDownloadBehind = errors.New("the download is behind the read")

// copyProgressive writes span from the growing file as its bytes arrive and
// returns the offset it got to.
func copyProgressive(ctx context.Context, w http.ResponseWriter, reader io.ReaderAt, file *progressiveFile, span byteSpan) (int64, error) {
	buffer := make([]byte, progressiveChunk)
	offset := span.start
	for offset <= span.end {
		size, err := file.waitBeyond(ctx, offset)
		if err != nil {
			return offset, fmt.Errorf("%w: %v", errDownloadBehind, err)
		}
		length := min(int64(len(buffer)), min(size-1, span.end)-offset+1)
		read, err := reader.ReadAt(buffer[:length], offset)
		if read == 0 && err != nil {
			return offset, err
		}
		if _, err := w.Write(buffer[:read]); err != nil {
			return offset, err
		}
		flush(w)
		offset += int64(read)
	}
	return offset, nil
}

// relaySpan writes span of the file from the CDN; the response headers are
// already out, so only a partial answer starting exactly at span.start fits.
func (p *Proxy) relaySpan(ctx context.Context, w http.ResponseWriter, upstream mp4Upstream, span byteSpan) error {
	rangeHeaders := withRange(upstream.headers, &byteRange{offset: span.start, length: span.end - span.start + 1})
	response, err := p.cache.fetch(ctx, upstream.url, rangeHeaders)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if !startsAt(response, span.start) {
		return fmt.Errorf("the CDN answered %d without the range", response.StatusCode)
	}
	_, err = io.Copy(w, response.Body)
	return err
}

func flush(w http.ResponseWriter) {
	if flusher, ok := w.(http.Flusher); ok {
		flusher.Flush()
	}
}

func writeSpanHeaders(w http.ResponseWriter, span byteSpan, total int64, partial bool) {
	w.Header().Set("Content-Type", "video/mp4")
	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Content-Length", strconv.FormatInt(span.end-span.start+1, 10))
	if !partial {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", span.start, span.end, total))
	w.WriteHeader(http.StatusPartialContent)
}

// requestedSpan reads a single-range Range header ("bytes=a-", "bytes=a-b",
// "bytes=-n"); no header is the whole file. ok is false for ranges this
// can't answer (multiple, unsatisfiable, malformed).
func requestedSpan(rangeHeader string, total int64) (byteSpan, bool) {
	whole := byteSpan{start: 0, end: total - 1}
	if rangeHeader == "" {
		return whole, true
	}
	spec, found := strings.CutPrefix(rangeHeader, "bytes=")
	if !found || strings.Contains(spec, ",") {
		return byteSpan{}, false
	}
	startText, endText, found := strings.Cut(strings.TrimSpace(spec), "-")
	if !found {
		return byteSpan{}, false
	}
	if startText == "" {
		return suffixSpan(endText, total)
	}
	return boundedSpan(startText, endText, total)
}

// suffixSpan is "bytes=-n": the last n bytes.
func suffixSpan(lengthText string, total int64) (byteSpan, bool) {
	length, err := strconv.ParseInt(lengthText, 10, 64)
	if err != nil || length <= 0 {
		return byteSpan{}, false
	}
	return byteSpan{start: max(total-length, 0), end: total - 1}, true
}

// boundedSpan is "bytes=a-" or "bytes=a-b", with b capped at the file's end.
func boundedSpan(startText, endText string, total int64) (byteSpan, bool) {
	start, err := strconv.ParseInt(startText, 10, 64)
	if err != nil || start < 0 || start >= total {
		return byteSpan{}, false
	}
	end := total - 1
	if endText != "" {
		end, err = strconv.ParseInt(endText, 10, 64)
		if err != nil || end < start {
			return byteSpan{}, false
		}
	}
	return byteSpan{start: start, end: min(end, total-1)}, true
}
