package subtitles

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/neoworks/otter/internal/blobcache"
)

const (
	cacheKeyPrefix  = "subtitle-cache/"
	downloadTimeout = 30 * time.Second
)

// EvictionSource describes the cleaned-file cache to the blob cache evictor:
// every file stands alone and is downloaded again when it is missing.
func EvictionSource() blobcache.Source {
	return blobcache.Files(cacheKeyPrefix)
}

var downloadClient = &http.Client{Timeout: downloadTimeout}

func cacheKey(id string) string {
	return cacheKeyPrefix + id + ".srt"
}

// Cues returns the cleaned cues of a stored file, downloading and cleaning
// the file on first use.
func (s *Service) Cues(ctx context.Context, id string) ([]Cue, error) {
	cleaned, err := s.cleanedSRT(ctx, id)
	if err != nil {
		return nil, err
	}
	return ParseCues(cleaned), nil
}

func (s *Service) cleanedSRT(ctx context.Context, id string) (string, error) {
	key := cacheKey(id)
	if s.blobs.Touch(ctx, key) == nil {
		return s.readCached(ctx, key)
	}
	cleaned, err, _ := s.downloads.Do(id, func() (any, error) {
		return s.downloadAndClean(context.WithoutCancel(ctx), id)
	})
	if err != nil {
		return "", err
	}
	return cleaned.(string), nil
}

func (s *Service) readCached(ctx context.Context, key string) (string, error) {
	reader, err := s.blobs.Get(ctx, key)
	if err != nil {
		return "", err
	}
	defer reader.Close()
	content, err := io.ReadAll(reader)
	return string(content), err
}

func (s *Service) downloadAndClean(ctx context.Context, id string) (string, error) {
	if err := s.allowed(); err != nil {
		return "", err
	}
	subtitle, err := s.store.Get(ctx, id)
	if err != nil {
		return "", err
	}
	if subtitle == nil {
		return "", ErrNotFound
	}
	raw, err := download(ctx, *subtitle)
	if err != nil {
		return "", err
	}
	text, err := DecodeText(raw, subtitle.Language)
	if err != nil {
		return "", err
	}
	cues := ParseCues(text)
	if len(cues) == 0 {
		return "", fmt.Errorf("subtitle %s has no captions", id)
	}
	cleaned := RenderSRT(cues)
	err = s.blobs.Put(ctx, cacheKey(id), bytes.NewReader([]byte(cleaned)), int64(len(cleaned)), "application/x-subrip")
	return cleaned, err
}

func download(ctx context.Context, subtitle Subtitle) ([]byte, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, subtitle.URL, nil)
	if err != nil {
		return nil, err
	}
	for name, value := range subtitle.Headers {
		request.Header.Set(name, value)
	}
	response, err := downloadClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("download %s: status %d", subtitle.Plugin, response.StatusCode)
	}
	content, err := io.ReadAll(io.LimitReader(response.Body, maxFileBytes+1))
	if err != nil {
		return nil, err
	}
	if len(content) > maxFileBytes {
		return nil, errFileTooLarge
	}
	return content, nil
}
