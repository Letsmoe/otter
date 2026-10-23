package subtitles

import (
	"context"
	"errors"
	"fmt"
	"log"
	"slices"
	"strings"
	"sync"
	"time"

	"golang.org/x/sync/singleflight"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

const (
	// pluginTimeout bounds one plugin's lookup; the API call waits for all.
	pluginTimeout = 20 * time.Second
	// lookupTTL is how long found files count as fresh.
	lookupTTL = 24 * time.Hour
	// emptyLookupTTL: finding nothing is usually temporary (a site hiccup).
	emptyLookupTTL  = 10 * time.Minute
	lookupKeyPrefix = "subtitles:lookup:"
)

// ArgsSource describes a movie or episode the way plugins search for it.
type ArgsSource interface {
	SearchArgs(ctx context.Context, ref library.MediaRef) (plugins.StreamsArgs, error)
}

// Service finds caption files and serves them cleaned up.
type Service struct {
	store    *Store
	registry *plugins.Registry
	runner   *plugins.Runner
	titles   ArgsSource
	cache    *cache.Client
	blobs    *storage.Client
	// allowed gates every outgoing request (the VPN requirement).
	allowed   func() error
	lookups   singleflight.Group
	downloads singleflight.Group
}

func NewService(store *Store, registry *plugins.Registry, runner *plugins.Runner, titles ArgsSource,
	cacheClient *cache.Client, blobs *storage.Client, allowed func() error) *Service {
	return &Service{
		store: store, registry: registry, runner: runner, titles: titles,
		cache: cacheClient, blobs: blobs, allowed: allowed,
	}
}

// MediaKey is the id the API knows a movie or episode by.
func MediaKey(ref library.MediaRef) string {
	return fmt.Sprintf("%s:%d", ref.MediaType, ref.MediaID)
}

// Find returns the best caption files of a movie or episode in the wanted
// languages, asking the subtitles plugins first when the stored files are
// stale or were looked up for other languages.
func (s *Service) Find(ctx context.Context, ref library.MediaRef, languages []string) ([]Subtitle, error) {
	mediaID := MediaKey(ref)
	if s.lookupDue(ctx, mediaID, languages) {
		// Keep earlier profiles' languages, so their files aren't replaced.
		wanted := mergeLanguages(s.askedLanguages(ctx, mediaID), languages)
		if err := s.lookup(ctx, ref, wanted); err != nil {
			return nil, err
		}
	}
	stored, err := s.store.ForMedia(ctx, mediaID)
	if err != nil {
		return nil, err
	}
	return Rank(stored, languages), nil
}

// lookupDue reports whether plugins should be asked: never asked, expired,
// or asked for fewer languages than wanted now.
func (s *Service) lookupDue(ctx context.Context, mediaID string, languages []string) bool {
	if s.allowed() != nil {
		return false
	}
	asked := s.askedLanguages(ctx, mediaID)
	if len(asked) == 0 {
		return true
	}
	askedSet := map[string]bool{}
	for _, language := range asked {
		askedSet[language] = true
	}
	for _, language := range languages {
		if !askedSet[language] {
			return true
		}
	}
	return false
}

// askedLanguages are the languages of the last lookup that is still fresh.
func (s *Service) askedLanguages(ctx context.Context, mediaID string) []string {
	asked, err := s.cache.Get(ctx, lookupKeyPrefix+mediaID)
	if err != nil || asked == "" {
		return nil
	}
	return strings.Split(asked, ",")
}

// mergeLanguages appends the wanted languages missing from earlier, keeping order.
func mergeLanguages(earlier []string, wanted []string) []string {
	merged := append([]string{}, earlier...)
	for _, language := range wanted {
		if !slices.Contains(merged, language) {
			merged = append(merged, language)
		}
	}
	return merged
}

func (s *Service) lookup(ctx context.Context, ref library.MediaRef, languages []string) error {
	_, err, _ := s.lookups.Do(MediaKey(ref), func() (any, error) {
		return nil, s.askPlugins(context.WithoutCancel(ctx), ref, languages)
	})
	return err
}

func (s *Service) askPlugins(ctx context.Context, ref library.MediaRef, languages []string) error {
	streamsArgs, err := s.titles.SearchArgs(ctx, ref)
	if err != nil {
		return err
	}
	args := subtitlesArgsOf(streamsArgs, languages)
	mediaID := MediaKey(ref)
	var waitGroup sync.WaitGroup
	for _, plugin := range s.registry.WithCapability(plugins.CapabilitySubtitles) {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			s.askPlugin(ctx, plugin, mediaID, args)
		}()
	}
	waitGroup.Wait()
	return s.markLookedUp(ctx, mediaID, languages)
}

func subtitlesArgsOf(args plugins.StreamsArgs, languages []string) plugins.SubtitlesArgs {
	return plugins.SubtitlesArgs{
		MediaType: args.MediaType, Title: args.Title, OriginalTitle: args.OriginalTitle,
		Year: args.Year, TMDBID: args.TMDBID, IMDBID: args.IMDBID,
		SeasonNumber: args.SeasonNumber, EpisodeNumber: args.EpisodeNumber,
		Languages: languages,
	}
}

func (s *Service) askPlugin(ctx context.Context, plugin *plugins.Plugin, mediaID string, args plugins.SubtitlesArgs) {
	pluginContext, cancel := context.WithTimeout(ctx, pluginTimeout)
	defer cancel()
	result, err := s.runner.Subtitles(pluginContext, plugin, args)
	if err != nil {
		log.Printf("subtitles: %s for %s: %v", plugin.Meta.Name, mediaID, err)
		return
	}
	files := usableFiles(result.Subtitles)
	if err := s.store.ReplacePluginFiles(ctx, mediaID, plugin.Meta.Name, files); err != nil {
		log.Printf("subtitles: save %s files: %v", plugin.Meta.Name, err)
	}
}

// usableFiles drops entries a plugin left incomplete and fills in the format.
func usableFiles(files []plugins.SubtitleFile) []plugins.SubtitleFile {
	usable := make([]plugins.SubtitleFile, 0, len(files))
	for _, file := range files {
		if file.URL == "" || file.Language == "" {
			continue
		}
		file.Language = strings.ToLower(file.Language)
		if file.Format == "" {
			file.Format = "srt"
		}
		usable = append(usable, file)
	}
	return usable
}

// markLookedUp remembers which languages were asked for, for as long as the
// outcome stays fresh.
func (s *Service) markLookedUp(ctx context.Context, mediaID string, languages []string) error {
	stored, err := s.store.ForMedia(ctx, mediaID)
	if err != nil {
		return err
	}
	ttl := lookupTTL
	if len(stored) == 0 {
		ttl = emptyLookupTTL
	}
	return s.cache.Set(ctx, lookupKeyPrefix+mediaID, strings.Join(languages, ","), ttl)
}

// ErrNotFound is returned for an id no stored file has.
var ErrNotFound = errors.New("subtitle not found")
