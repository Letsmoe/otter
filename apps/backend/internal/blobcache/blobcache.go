// Package blobcache caps the disk space taken by cache blobs (stream and image
// caches) in the blob store. Once their total passes the limit, the least
// recently used ones are deleted until it drops to the low-water mark.
//
// Each cache registers a Source for its key prefix. The source groups its
// files into units that are evicted together (e.g. an HLS manifest with all its
// segments), marks units that must stay (user downloads, streams being written)
// and drops its index entries before a unit's files are deleted. Blobs outside
// every source prefix (posters, images, plugin icons) are never touched.
package blobcache

import (
	"context"
	"log"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/neoworks/otter/internal/storage"
)

// DefaultMaxBytes is the cache size limit when CACHE_MAX_BYTES is unset.
const DefaultMaxBytes int64 = 300 << 20

// lowWaterPercent is the share of the limit an eviction pass shrinks the cache
// to, so the next few writes don't immediately trigger another pass.
const lowWaterPercent = 90

// RecentUseGrace protects blobs used this recently from eviction, so a stream
// that is playing or an image on screen is never deleted from under the client.
const RecentUseGrace = 5 * time.Minute

const (
	// scanInterval is how often the cache is measured regardless of writes.
	scanInterval = 15 * time.Minute
	// writeDebounce delays the pass a write over the limit triggers, so a
	// burst of segment writes costs one pass.
	writeDebounce = 10 * time.Second
)

// Unit is a set of blobs evicted together.
type Unit struct {
	Files []storage.File
	// Pinned units are user content: never evicted, not counted against the limit.
	Pinned bool
	// Active units are in use right now (a stream being played or cached):
	// neither evicted nor counted against the limit until they go idle.
	Active bool
	// Stale units are unusable (e.g. an HLS stream missing segments) and are
	// evicted on every pass, whatever the cache size.
	Stale bool
}

// Size is the total size of the unit's files.
func (u Unit) Size() int64 {
	var total int64
	for _, file := range u.Files {
		total += file.Size
	}
	return total
}

// LastUsed is the latest use of any of the unit's files.
func (u Unit) LastUsed() time.Time {
	var latest time.Time
	for _, file := range u.Files {
		if file.LastUsed.After(latest) {
			latest = file.LastUsed
		}
	}
	return latest
}

// Source is one cache living under a blob key prefix.
type Source interface {
	// Prefix is the blob key prefix the cache owns, ending in "/".
	Prefix() string
	// Units groups every file under Prefix into eviction units. An error skips
	// the source for this pass.
	Units(ctx context.Context, files []storage.File) ([]Unit, error)
	// Invalidate drops whatever points at the unit's files; it runs before
	// they are deleted. An error keeps the unit.
	Invalidate(ctx context.Context, unit Unit) error
}

// Files is a Source for a cache whose blobs stand alone: each file is its own
// unit and nothing indexes them (a missing blob is simply fetched again).
func Files(prefix string) Source {
	return fileSource{prefix: prefix}
}

type fileSource struct {
	prefix string
}

// Prefix returns the cache's key prefix.
func (s fileSource) Prefix() string {
	return s.prefix
}

// Units makes each file its own unit.
func (s fileSource) Units(ctx context.Context, files []storage.File) ([]Unit, error) {
	units := make([]Unit, 0, len(files))
	for _, file := range files {
		units = append(units, Unit{Files: []storage.File{file}})
	}
	return units, nil
}

// Invalidate has nothing to drop.
func (s fileSource) Invalidate(ctx context.Context, unit Unit) error {
	return nil
}

// Evictor keeps the cache sources' total size under a limit.
type Evictor struct {
	store    *storage.Client
	maxBytes int64
	sources  []Source
	now      func() time.Time
	debounce time.Duration

	// passMutex serialises eviction passes.
	passMutex sync.Mutex
	// estimate is the cache size as of the last pass plus bytes written since;
	// it only decides whether a write should trigger a pass.
	estimate atomic.Int64
	// writes wakes Run when a write pushed the estimate over the limit.
	writes chan struct{}
}

// Report summarises one eviction pass.
type Report struct {
	TotalBytes   int64
	EvictedUnits int
	FreedBytes   int64
}

// New returns an evictor holding the sources' blobs in store to maxBytes.
func New(store *storage.Client, maxBytes int64, sources ...Source) *Evictor {
	return &Evictor{
		store:    store,
		maxBytes: maxBytes,
		sources:  sources,
		now:      time.Now,
		debounce: writeDebounce,
		writes:   make(chan struct{}, 1),
	}
}

// NoteWrite accounts for a blob written to the store (wire it to
// storage.Client.OnPut) and schedules a pass once the cache may be too big.
func (e *Evictor) NoteWrite(key string, size int64) {
	if e.sourceFor(key) == nil {
		return
	}
	if e.estimate.Add(size) <= e.maxBytes {
		return
	}
	select {
	case e.writes <- struct{}{}:
	default:
	}
}

// Run evicts once now, then periodically and shortly after writes that push
// the cache over the limit, until ctx ends.
func (e *Evictor) Run(ctx context.Context) {
	e.evictLogged(ctx)
	ticker := time.NewTicker(scanInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			e.evictLogged(ctx)
		case <-e.writes:
			if !sleep(ctx, e.debounce) {
				return
			}
			e.drainWrites()
			e.evictLogged(ctx)
		}
	}
}

// Evict measures every source and deletes stale units, then, when the total is
// over the limit, the least recently used units until it is under the
// low-water mark. Pinned, active and recently used units are kept.
func (e *Evictor) Evict(ctx context.Context) (Report, error) {
	e.passMutex.Lock()
	defer e.passMutex.Unlock()

	candidates, total, err := e.collect(ctx)
	if err != nil {
		return Report{}, err
	}
	report := Report{}
	lowWater := e.maxBytes * lowWaterPercent / 100
	overLimit := total > e.maxBytes
	for _, candidate := range e.evictable(candidates) {
		if !candidate.unit.Stale && (!overLimit || total <= lowWater) {
			continue
		}
		if !e.remove(ctx, candidate) {
			continue
		}
		size := candidate.unit.Size()
		total -= size
		report.EvictedUnits++
		report.FreedBytes += size
	}
	report.TotalBytes = total
	e.estimate.Store(total)
	return report, nil
}

// candidate is a unit together with the source that owns it.
type candidate struct {
	source Source
	unit   Unit
}

// collect lists every source's units that count against the limit, and their
// total size. A source that fails is logged and left alone this pass.
func (e *Evictor) collect(ctx context.Context) ([]candidate, int64, error) {
	var candidates []candidate
	var total int64
	for _, source := range e.sources {
		files, err := e.store.List(ctx, source.Prefix())
		if err != nil {
			return nil, 0, err
		}
		units, err := source.Units(ctx, files)
		if err != nil {
			log.Printf("blob cache %s: %v", source.Prefix(), err)
			continue
		}
		for _, unit := range units {
			if unit.Pinned || unit.Active {
				continue
			}
			total += unit.Size()
			candidates = append(candidates, candidate{source: source, unit: unit})
		}
	}
	return candidates, total, nil
}

// evictable filters candidates down to those that may go now, stale ones
// first, then least recently used first.
func (e *Evictor) evictable(candidates []candidate) []candidate {
	usedBefore := e.now().Add(-RecentUseGrace)
	var result []candidate
	for _, candidate := range candidates {
		if !candidate.unit.Stale && candidate.unit.LastUsed().After(usedBefore) {
			continue
		}
		result = append(result, candidate)
	}
	sort.SliceStable(result, func(left, right int) bool {
		if result[left].unit.Stale != result[right].unit.Stale {
			return result[left].unit.Stale
		}
		return result[left].unit.LastUsed().Before(result[right].unit.LastUsed())
	})
	return result
}

// remove invalidates a unit and deletes its files; it reports whether the
// unit is gone.
func (e *Evictor) remove(ctx context.Context, candidate candidate) bool {
	if err := candidate.source.Invalidate(ctx, candidate.unit); err != nil {
		log.Printf("blob cache: invalidate %s: %v", candidate.unit.Files[0].Key, err)
		return false
	}
	for _, file := range candidate.unit.Files {
		if err := e.store.Delete(ctx, file.Key); err != nil {
			log.Printf("blob cache: delete %s: %v", file.Key, err)
		}
	}
	return true
}

// evictLogged runs a pass and logs what it freed or why it failed.
func (e *Evictor) evictLogged(ctx context.Context) {
	report, err := e.Evict(ctx)
	if err != nil {
		log.Printf("blob cache: eviction: %v", err)
		return
	}
	if report.EvictedUnits > 0 {
		log.Printf("blob cache: evicted %d entries (%d MB), %d MB left",
			report.EvictedUnits, report.FreedBytes>>20, report.TotalBytes>>20)
	}
}

// sourceFor returns the source owning key, or nil.
func (e *Evictor) sourceFor(key string) Source {
	for _, source := range e.sources {
		if strings.HasPrefix(key, source.Prefix()) {
			return source
		}
	}
	return nil
}

// drainWrites clears a write signal that arrived during the debounce; the
// coming pass covers it.
func (e *Evictor) drainWrites() {
	select {
	case <-e.writes:
	default:
	}
}

// sleep waits for duration and reports false if ctx ended first.
func sleep(ctx context.Context, duration time.Duration) bool {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
