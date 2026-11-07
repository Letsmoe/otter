package stream

import (
	"bytes"
	"context"
	"crypto/aes"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
)

// downloadHLS saves a media playlist's segments decrypted, with a playlist
// that only points at local blobs, so the download plays offline, on a
// Chromecast and on DLNA TVs without asking the site for keys again.
func (s *CacheService) downloadHLS(ctx context.Context, manifestURL string, headers map[string]string, resumeKey string, progress func(float64, int64, int64)) (string, error) {
	content, err := s.fetchManifest(ctx, manifestURL, headers)
	if err != nil {
		return "", err
	}
	base, _ := url.Parse(manifestURL)
	playlist, err := parseMediaPlaylist(content, base)
	if err != nil {
		return "", fmt.Errorf("read manifest: %w", err)
	}
	blobKeys := downloadSegmentKeys(playlist.segments, resumeKey)
	aesKeys, err := s.fetchSegmentKeys(ctx, playlist.segments, headers)
	if err != nil {
		return "", fmt.Errorf("fetch decryption key: %w", err)
	}
	if err := s.downloadSegments(ctx, playlist.segments, blobKeys, headers, aesKeys, progress); err != nil {
		return "", fmt.Errorf("download segments: %w", err)
	}
	initBlobURL, err := s.downloadInit(ctx, playlist.initURL, headers, resumeKey)
	if err != nil {
		return "", fmt.Errorf("download init segment: %w", err)
	}
	manifest := s.localPlaylist(content, blobKeys, initBlobURL)
	manifestKey := blobPrefix + "manifest/" + urlHash("download:"+resumeKey) + ".m3u8"
	if err := s.storage.Put(ctx, manifestKey, strings.NewReader(manifest), int64(len(manifest)), "application/x-mpegURL"); err != nil {
		return "", fmt.Errorf("save manifest: %w", err)
	}
	return s.storage.DirectURL(manifestKey), nil
}

func (s *CacheService) fetchManifest(ctx context.Context, manifestURL string, headers map[string]string) (string, error) {
	resp, err := s.fetch(ctx, manifestURL, headers)
	if err != nil {
		return "", fmt.Errorf("fetch manifest %s: %w", manifestURL, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("read manifest: %w", err)
	}
	return string(body), nil
}

// downloadSegmentKeys names segments by link, segment count and position
// instead of by URL, whose signed tokens change with every resolve. Decrypted
// and range-cut segments get names of their own, so a resumed download never
// mixes them with raw ones stored before downloads decrypted.
func downloadSegmentKeys(segments []mediaSegment, resumeKey string) []string {
	keys := make([]string, len(segments))
	for index, segment := range segments {
		identity := fmt.Sprintf("download:%s#%d/%d", resumeKey, index, len(segments))
		if segment.key != nil || segment.byteRange != nil {
			identity = "decoded:" + identity
		}
		keys[index] = blobPrefix + "seg/" + urlHash(identity) + segExt(segment.url)
	}
	return keys
}

// fetchSegmentKeys fetches each AES-128 key once, while the resolved
// stream's headers and tokens still work.
func (s *CacheService) fetchSegmentKeys(ctx context.Context, segments []mediaSegment, headers map[string]string) (map[string][]byte, error) {
	keys := map[string][]byte{}
	for _, segment := range segments {
		if segment.key == nil || keys[segment.key.uri] != nil {
			continue
		}
		key, err := s.fetchSegmentKey(ctx, segment.key.uri, headers)
		if err != nil {
			return nil, err
		}
		keys[segment.key.uri] = key
	}
	return keys, nil
}

func (s *CacheService) fetchSegmentKey(ctx context.Context, keyURL string, headers map[string]string) ([]byte, error) {
	resp, err := s.fetch(ctx, keyURL, headers)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	key, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if len(key) != aes.BlockSize {
		return nil, fmt.Errorf("key is %d bytes, want %d", len(key), aes.BlockSize)
	}
	return key, nil
}

// downloadSegments stores every segment not stored yet; ones kept from an
// interrupted run count as done right away.
func (s *CacheService) downloadSegments(ctx context.Context, segments []mediaSegment, blobKeys []string, headers map[string]string, aesKeys map[string][]byte, progress func(float64, int64, int64)) error {
	tracker := &segmentProgress{total: len(segments), report: progress}
	var (
		workers     sync.WaitGroup
		errorsMutex sync.Mutex
		failures    []error
		slots       = make(chan struct{}, segWorkers)
	)
	for index, segment := range segments {
		if s.storage.Touch(ctx, blobKeys[index]) == nil {
			tracker.add(s.storage.Size(blobKeys[index]))
			continue
		}
		workers.Add(1)
		slots <- struct{}{}
		go func(segment mediaSegment, blobKey string) {
			defer workers.Done()
			defer func() { <-slots }()
			size, err := s.downloadSegment(ctx, segment, blobKey, headers, aesKeys)
			if err != nil {
				errorsMutex.Lock()
				failures = append(failures, err)
				errorsMutex.Unlock()
				return
			}
			tracker.add(size)
		}(segment, blobKeys[index])
	}
	workers.Wait()
	if len(failures) > 0 {
		return fmt.Errorf("%d/%d segments failed (first: %w)", len(failures), len(segments), failures[0])
	}
	return nil
}

// downloadSegment stores one segment. Plain segments stream straight to disk;
// encrypted or byte-range ones are read whole, cut and decrypted first.
func (s *CacheService) downloadSegment(ctx context.Context, segment mediaSegment, blobKey string, headers map[string]string, aesKeys map[string][]byte) (int64, error) {
	if segment.key == nil && segment.byteRange == nil {
		return s.storeResponse(ctx, segment.url, blobKey, headers)
	}
	body, err := s.fetchSegmentBytes(ctx, segment, headers)
	if err != nil {
		return 0, err
	}
	if segment.key != nil {
		body, err = decryptAES128(body, aesKeys[segment.key.uri], segmentIV(segment))
		if err != nil {
			return 0, fmt.Errorf("decrypt segment: %w", err)
		}
	}
	if err := s.storage.Put(ctx, blobKey, bytes.NewReader(body), int64(len(body)), "video/MP2T"); err != nil {
		return 0, fmt.Errorf("save segment: %w", err)
	}
	return int64(len(body)), nil
}

func (s *CacheService) storeResponse(ctx context.Context, rawURL, blobKey string, headers map[string]string) (int64, error) {
	resp, err := s.fetch(ctx, rawURL, headers)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if err := s.storage.Put(ctx, blobKey, resp.Body, resp.ContentLength, "video/MP2T"); err != nil {
		return 0, fmt.Errorf("save segment: %w", err)
	}
	return s.storage.Size(blobKey), nil
}

// fetchSegmentBytes reads a segment, only its byte range when it has one.
func (s *CacheService) fetchSegmentBytes(ctx context.Context, segment mediaSegment, headers map[string]string) ([]byte, error) {
	if segment.byteRange == nil {
		return s.fetchAll(ctx, segment.url, headers)
	}
	resp, err := s.fetch(ctx, segment.url, withRange(headers, segment.byteRange))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode == http.StatusPartialContent {
		return body, nil
	}
	return cutRange(body, segment.byteRange)
}

func (s *CacheService) fetchAll(ctx context.Context, rawURL string, headers map[string]string) ([]byte, error) {
	resp, err := s.fetch(ctx, rawURL, headers)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	return io.ReadAll(resp.Body)
}

// cutRange takes a segment's bytes out of a whole file, for servers that
// ignore the Range header.
func cutRange(body []byte, segmentRange *byteRange) ([]byte, error) {
	end := segmentRange.offset + segmentRange.length
	if segmentRange.offset < 0 || end > int64(len(body)) {
		return nil, fmt.Errorf("byte range %d@%d outside the %d-byte file", segmentRange.length, segmentRange.offset, len(body))
	}
	return body[segmentRange.offset:end], nil
}

// downloadInit stores the init segment of an fMP4 playlist; "" without one.
func (s *CacheService) downloadInit(ctx context.Context, initURL string, headers map[string]string, resumeKey string) (string, error) {
	if initURL == "" {
		return "", nil
	}
	blobKey := blobPrefix + "seg/" + urlHash("download-init:"+resumeKey) + segExt(initURL)
	if s.storage.Touch(ctx, blobKey) == nil {
		return s.storage.DirectURL(blobKey), nil
	}
	if _, err := s.storeResponse(ctx, initURL, blobKey, headers); err != nil {
		return "", err
	}
	return s.storage.DirectURL(blobKey), nil
}

// localPlaylist rewrites a media playlist to the stored blobs: segment lines
// become blob URLs and the init segment its blob. Keys and byte ranges go, as
// the stored segments are decrypted and cut already. Other tags (durations,
// discontinuities) stay.
func (s *CacheService) localPlaylist(content string, blobKeys []string, initBlobURL string) string {
	var lines []string
	next := 0
	for _, rawLine := range strings.Split(content, "\n") {
		line := strings.TrimSpace(rawLine)
		switch {
		case strings.HasPrefix(line, "#EXT-X-KEY"), strings.HasPrefix(line, "#EXT-X-BYTERANGE"):
			continue
		case strings.HasPrefix(line, "#EXT-X-MAP"):
			lines = append(lines, `#EXT-X-MAP:URI="`+initBlobURL+`"`)
		case line == "" || strings.HasPrefix(line, "#"):
			lines = append(lines, line)
		case next < len(blobKeys):
			lines = append(lines, s.storage.DirectURL(blobKeys[next]))
			next++
		}
	}
	return strings.Join(lines, "\n")
}
