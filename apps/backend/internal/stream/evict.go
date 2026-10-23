package stream

import (
	"context"
	"io"
	"path"
	"strings"
	"time"

	"github.com/neoworks/otter/internal/blobcache"
	"github.com/neoworks/otter/internal/storage"
)

// busyMargin widens a running cache operation's window backwards: blobs used
// this long before it started count as its own. It covers the blob store's
// throttled touches and coarse file-system timestamps.
const busyMargin = 2 * time.Minute

// SetPinnedURLs registers where to find the blob URLs of user downloads; the
// cached streams they point at are never evicted.
func (s *CacheService) SetPinnedURLs(pinnedURLs func(ctx context.Context) ([]string, error)) {
	s.pinnedURLs = pinnedURLs
}

// EvictionSource describes the stream cache to the blob cache evictor.
func (s *CacheService) EvictionSource() blobcache.Source {
	return evictionSource{service: s}
}

// beginOperation records a cache operation starting now; call the returned
// function when it ends.
func (s *CacheService) beginOperation() func() {
	s.operationsMutex.Lock()
	defer s.operationsMutex.Unlock()
	if s.operations == nil {
		s.operations = map[int]time.Time{}
	}
	id := s.nextOperation
	s.nextOperation++
	s.operations[id] = time.Now()
	return func() {
		s.operationsMutex.Lock()
		defer s.operationsMutex.Unlock()
		delete(s.operations, id)
	}
}

// activeSince returns the time from which a blob's last use makes its stream
// active: within blobcache.RecentUseGrace (being played), or since the oldest
// running cache operation started, less busyMargin (being cached).
func (s *CacheService) activeSince() time.Time {
	since := time.Now().Add(-blobcache.RecentUseGrace)
	s.operationsMutex.Lock()
	defer s.operationsMutex.Unlock()
	for _, started := range s.operations {
		if started.Add(-busyMargin).Before(since) {
			since = started.Add(-busyMargin)
		}
	}
	return since
}

// pinnedKeys returns the blob keys of user downloads.
func (s *CacheService) pinnedKeys(ctx context.Context) (map[string]bool, error) {
	keys := map[string]bool{}
	if s.pinnedURLs == nil {
		return keys, nil
	}
	urls, err := s.pinnedURLs(ctx)
	if err != nil {
		return nil, err
	}
	for _, blobURL := range urls {
		if key, ok := storage.KeyFromURL(blobURL); ok {
			keys[key] = true
		}
	}
	return keys, nil
}

// evictionSource groups the stream cache into streams: an HLS manifest with
// the variant manifests and segments it references is one unit, an MP4 or a
// segment only the playback proxy wrote is a unit of its own.
type evictionSource struct {
	service *CacheService
}

// Prefix returns the stream cache's key prefix.
func (source evictionSource) Prefix() string {
	return blobPrefix
}

// Units groups files into streams and marks downloads pinned, streams being
// played or cached active, and manifests missing a referenced blob stale.
func (source evictionSource) Units(ctx context.Context, files []storage.File) ([]blobcache.Unit, error) {
	pinned, err := source.service.pinnedKeys(ctx)
	if err != nil {
		return nil, err
	}
	groups := newFileGroups(files)
	for _, file := range files {
		if strings.HasPrefix(file.Key, blobPrefix+"manifest/") {
			source.linkManifest(ctx, groups, file.Key)
		}
	}
	activeSince := source.service.activeSince()
	units := groups.units()
	for index := range units {
		unit := &units[index]
		unit.Pinned = containsKey(unit.Files, pinned)
		unit.Active = !unit.LastUsed().Before(activeSince)
		unit.Stale = groups.broken[groups.find(unit.Files[0].Key)]
	}
	return units, nil
}

// linkManifest joins a manifest with the blobs it references, or marks it
// broken when one of them is gone.
func (source evictionSource) linkManifest(ctx context.Context, groups *fileGroups, manifestKey string) {
	references, err := source.service.manifestReferences(ctx, manifestKey)
	if err != nil {
		// Deleted since it was listed; nothing to link.
		return
	}
	for _, reference := range references {
		if !groups.contains(reference) {
			groups.markBroken(manifestKey)
			continue
		}
		groups.union(manifestKey, reference)
	}
}

// Invalidate drops the kv entries that point at the unit's blobs, so lookups
// and the playback proxy fall back to the origin.
func (source evictionSource) Invalidate(ctx context.Context, unit blobcache.Unit) error {
	var kvKeys []string
	for _, file := range unit.Files {
		if kvKey, ok := entryKey(file.Key); ok {
			kvKeys = append(kvKeys, kvKey)
		}
	}
	return source.service.kv.Del(ctx, kvKeys...)
}

// manifestReferences returns the stream cache blob keys a cached manifest
// points at: segments, or a master's variant manifests.
func (s *CacheService) manifestReferences(ctx context.Context, manifestKey string) ([]string, error) {
	reader, err := s.storage.Get(ctx, manifestKey)
	if err != nil {
		return nil, err
	}
	defer reader.Close()
	content, err := io.ReadAll(reader)
	if err != nil {
		return nil, err
	}
	var references []string
	for _, line := range strings.Split(string(content), "\n") {
		key, ok := storage.KeyFromURL(strings.TrimSpace(line))
		if ok && strings.HasPrefix(key, blobPrefix) {
			references = append(references, key)
		}
	}
	return references, nil
}

// entryKey returns the kv key that marks a stream cache blob as cached:
// the lookup entry for manifests and MP4s, the segment flag for segments.
func entryKey(blobKey string) (string, bool) {
	hash := strings.TrimSuffix(path.Base(blobKey), path.Ext(blobKey))
	switch {
	case strings.HasPrefix(blobKey, blobPrefix+"seg/"):
		return kvSegmentPrefix + hash, true
	case strings.HasPrefix(blobKey, blobPrefix+"manifest/"), strings.HasPrefix(blobKey, blobPrefix+"mp4/"):
		return kvPrefix + hash, true
	default:
		return "", false
	}
}

// containsKey reports whether any of files is in keys.
func containsKey(files []storage.File, keys map[string]bool) bool {
	for _, file := range files {
		if keys[file.Key] {
			return true
		}
	}
	return false
}

// fileGroups is a union-find over blob keys, joining files that must be
// evicted together.
type fileGroups struct {
	files  map[string]storage.File
	order  []string
	parent map[string]string
	broken map[string]bool
}

// newFileGroups starts every file in a group of its own.
func newFileGroups(files []storage.File) *fileGroups {
	groups := &fileGroups{
		files:  make(map[string]storage.File, len(files)),
		parent: make(map[string]string, len(files)),
		broken: map[string]bool{},
	}
	for _, file := range files {
		groups.files[file.Key] = file
		groups.parent[file.Key] = file.Key
		groups.order = append(groups.order, file.Key)
	}
	return groups
}

// contains reports whether key is one of the grouped files.
func (g *fileGroups) contains(key string) bool {
	_, ok := g.files[key]
	return ok
}

// find returns the key representing key's group.
func (g *fileGroups) find(key string) string {
	for g.parent[key] != key {
		g.parent[key] = g.parent[g.parent[key]]
		key = g.parent[key]
	}
	return key
}

// union joins the groups of two keys, keeping a broken mark.
func (g *fileGroups) union(first, second string) {
	firstRoot := g.find(first)
	secondRoot := g.find(second)
	if firstRoot == secondRoot {
		return
	}
	g.parent[secondRoot] = firstRoot
	if g.broken[secondRoot] {
		g.broken[firstRoot] = true
	}
}

// markBroken flags key's group as unusable.
func (g *fileGroups) markBroken(key string) {
	g.broken[g.find(key)] = true
}

// units returns one unit per group, in listing order.
func (g *fileGroups) units() []blobcache.Unit {
	indexByRoot := map[string]int{}
	var units []blobcache.Unit
	for _, key := range g.order {
		root := g.find(key)
		index, ok := indexByRoot[root]
		if !ok {
			index = len(units)
			indexByRoot[root] = index
			units = append(units, blobcache.Unit{})
		}
		units[index].Files = append(units[index].Files, g.files[key])
	}
	return units
}
