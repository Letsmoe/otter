package stream

import (
	"context"
	"encoding/json"
	"path"
	"strings"

	"github.com/neoworks/otter/internal/storage"
)

// maxManifestDepth bounds how far blobComplete follows manifests: a master
// playlist references variant playlists, which reference segments.
const maxManifestDepth = 2

// blobComplete reports whether a cached blob is present and, for a manifest,
// every blob it references (recursively) too, so a stream missing a single
// segment is not handed out as cached.
func (s *CacheService) blobComplete(ctx context.Context, key string) bool {
	return s.blobCompleteWithin(ctx, key, maxManifestDepth)
}

// blobCompleteWithin is blobComplete following at most depth manifest levels.
func (s *CacheService) blobCompleteWithin(ctx context.Context, key string, depth int) bool {
	if !s.storage.Exists(ctx, key) {
		return false
	}
	if !strings.HasPrefix(key, blobPrefix+"manifest/") || depth == 0 {
		return true
	}
	references, err := s.manifestReferences(ctx, key)
	if err != nil {
		return false
	}
	for _, reference := range references {
		if !s.blobCompleteWithin(ctx, reference, depth-1) {
			return false
		}
	}
	return true
}

// Reconcile drops kv entries that point at stream cache blobs no longer on
// disk (deleted by hand, or lost in a crash): lookup entries whose stream is
// incomplete and segment flags whose file is gone. It returns how many went.
func (s *CacheService) Reconcile(ctx context.Context) (int, error) {
	staleEntries, err := s.staleLookupEntries(ctx)
	if err != nil {
		return 0, err
	}
	staleFlags, err := s.staleSegmentFlags(ctx)
	if err != nil {
		return 0, err
	}
	stale := append(staleEntries, staleFlags...)
	return len(stale), s.kv.Del(ctx, stale...)
}

// staleLookupEntries returns the lookup entries whose stream is incomplete.
func (s *CacheService) staleLookupEntries(ctx context.Context) ([]string, error) {
	entries, err := s.kv.Entries(ctx, kvPrefix)
	if err != nil {
		return nil, err
	}
	var stale []string
	for kvKey, value := range entries {
		var entry cacheEntry
		if json.Unmarshal([]byte(value), &entry) != nil || !s.blobComplete(ctx, entry.Key) {
			stale = append(stale, kvKey)
		}
	}
	return stale, nil
}

// staleSegmentFlags returns the segment flags whose segment file is gone.
func (s *CacheService) staleSegmentFlags(ctx context.Context) ([]string, error) {
	flags, err := s.kv.Entries(ctx, kvSegmentPrefix)
	if err != nil {
		return nil, err
	}
	files, err := s.storage.List(ctx, blobPrefix+"seg/")
	if err != nil {
		return nil, err
	}
	stored := make(map[string]bool, len(files))
	for _, file := range files {
		stored[strings.TrimSuffix(path.Base(file.Key), path.Ext(file.Key))] = true
	}
	var stale []string
	for kvKey := range flags {
		if !stored[strings.TrimPrefix(kvKey, kvSegmentPrefix)] {
			stale = append(stale, kvKey)
		}
	}
	return stale, nil
}

// Complete reports whether blobURL, when it is a stream cache blob, is on disk
// with everything it references. Any other URL counts as complete.
func (s *CacheService) Complete(ctx context.Context, blobURL string) bool {
	if !strings.HasPrefix(blobURL, s.storage.DirectURL(blobPrefix)) {
		return true
	}
	key, _ := storage.KeyFromURL(blobURL)
	return s.blobComplete(ctx, key)
}
