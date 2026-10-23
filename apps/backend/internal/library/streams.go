package library

import (
	"context"
	"fmt"
	"log"
	"slices"
	"strings"
	"sync"
	"time"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/hooks"
	"github.com/neoworks/otter/internal/plugins"
)

// pluginTimeout bounds one plugin's streams lookup; slow sites must not hold
// up the others. Links are announced per plugin, so a slow site only delays
// its own links: kinoking takes 10-40 s to compile an uncached episode.
const pluginTimeout = 90 * time.Second

// FindStreamsIfStale runs FindStreams unless plugins were asked recently.
// lookedUp reports whether a lookup ran (and so announced its final update).
func (s *Service) FindStreamsIfStale(ctx context.Context, ref MediaRef) (lookedUp bool, err error) {
	fetchedAt, err := s.Store.StreamsFetchedAt(ctx, ref)
	if err != nil {
		return false, err
	}
	ttl, err := s.streamsTTLFor(ctx, ref)
	if err != nil {
		return false, err
	}
	if !isStale(fetchedAt, ttl) {
		return false, nil
	}
	return true, s.FindStreams(ctx, ref)
}

// streamsTTLFor keeps found links for streamsTTL, but asks again soon when
// the last lookup found nothing.
func (s *Service) streamsTTLFor(ctx context.Context, ref MediaRef) (time.Duration, error) {
	stored, err := s.Store.Streams(ctx, ref)
	if err != nil {
		return 0, err
	}
	if len(stored) == 0 {
		return emptyStreamsTTL, nil
	}
	return streamsTTL, nil
}

const streamsPluginsKey = "library:streams-plugins"

// ExpireStreamsIfPluginsChanged makes every title ask the plugins again when
// the streams plugins or their versions differ from the last start, so new
// and fixed sources show up right after an update.
func (s *Service) ExpireStreamsIfPluginsChanged(ctx context.Context, kv *cache.Client) error {
	fingerprint := s.streamsPluginsFingerprint()
	previous, _ := kv.Get(ctx, streamsPluginsKey)
	if previous == fingerprint {
		return nil
	}
	if err := s.Store.ExpireAllStreams(ctx); err != nil {
		return err
	}
	log.Printf("library: streams plugins changed, every title looks for streams again")
	return kv.Set(ctx, streamsPluginsKey, fingerprint, 0)
}

// streamsPluginsFingerprint is "name@version" of every streams plugin, sorted.
func (s *Service) streamsPluginsFingerprint() string {
	var entries []string
	for _, plugin := range s.plugins.WithCapability(plugins.CapabilityStreams) {
		entries = append(entries, plugin.Meta.Name+"@"+plugin.Meta.Version)
	}
	slices.Sort(entries)
	return strings.Join(entries, ",")
}

// FindStreams asks every active streams plugin for a movie or episode in
// parallel. Each plugin's links are stored and announced on StreamUpdates
// as soon as that plugin answers. Concurrent calls for one title share a run.
func (s *Service) FindStreams(ctx context.Context, ref MediaRef) error {
	if err := s.streamsAllowed(); err != nil {
		return err
	}
	key := fmt.Sprintf("streams:%s:%d", ref.MediaType, ref.MediaID)
	_, err, _ := s.inflight.Do(key, func() (any, error) {
		s.setLookingUp(ref, true)
		defer s.setLookingUp(ref, false)
		return nil, s.findStreams(context.WithoutCancel(ctx), ref)
	})
	return err
}

func (s *Service) setLookingUp(ref MediaRef, running bool) {
	s.lookupsMutex.Lock()
	defer s.lookupsMutex.Unlock()
	if running {
		s.lookups[ref] = true
		return
	}
	delete(s.lookups, ref)
}

func (s *Service) isLookingUp(ref MediaRef) bool {
	s.lookupsMutex.Lock()
	defer s.lookupsMutex.Unlock()
	return s.lookups[ref]
}

// AnnounceStreams sends a title's stored links to its subscribers again,
// e.g. after links were checked and got a verdict, speed and resolution.
func (s *Service) AnnounceStreams(ctx context.Context, ref MediaRef) {
	streams, err := s.Store.Streams(ctx, ref)
	if err != nil {
		return
	}
	s.StreamUpdates.Publish(ref, StreamUpdate{Streams: streams, Done: !s.isLookingUp(ref)})
}

func (s *Service) findStreams(ctx context.Context, ref MediaRef) error {
	args, err := s.streamsArgs(ctx, ref)
	if err != nil {
		return err
	}
	var waitGroup sync.WaitGroup
	for _, plugin := range s.plugins.WithCapability(plugins.CapabilityStreams) {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			s.askPlugin(ctx, plugin, ref, args)
		}()
	}
	waitGroup.Wait()
	if err := s.Store.MarkStreamsFetched(ctx, ref); err != nil {
		return err
	}
	streams, err := s.Store.Streams(ctx, ref)
	if err != nil {
		return err
	}
	s.StreamUpdates.Publish(ref, StreamUpdate{Streams: streams, Done: true})
	// Payload: the title reference, its search arguments and every stream.
	s.hooks.Emit(hooks.EventStreamsFound, map[string]any{"media": ref, "query": args, "streams": streams})
	return nil
}

func (s *Service) askPlugin(ctx context.Context, plugin *plugins.Plugin, ref MediaRef, args plugins.StreamsArgs) {
	pluginContext, cancel := context.WithTimeout(ctx, pluginTimeout)
	defer cancel()
	result, err := s.runner.Streams(pluginContext, plugin, args)
	if err != nil {
		log.Printf("library: %s streams for %s %d: %v", plugin.Meta.Name, ref.MediaType, ref.MediaID, err)
		return
	}
	if err := s.Store.ReplacePluginStreams(ctx, ref, plugin.Meta.Name, result.Downloads); err != nil {
		log.Printf("library: save %s streams: %v", plugin.Meta.Name, err)
		return
	}
	streams, err := s.Store.Streams(ctx, ref)
	if err != nil {
		return
	}
	s.StreamUpdates.Publish(ref, StreamUpdate{Streams: streams})
}

// streamsArgs describes a movie or episode the way plugins search for it.
func (s *Service) streamsArgs(ctx context.Context, ref MediaRef) (plugins.StreamsArgs, error) {
	switch ref.MediaType {
	case MediaTypeMovie:
		return s.movieStreamsArgs(ctx, ref.MediaID)
	case MediaTypeEpisode:
		return s.episodeStreamsArgs(ctx, ref.MediaID)
	}
	return plugins.StreamsArgs{}, fmt.Errorf("streams need a movie or episode, got %q", ref.MediaType)
}

func (s *Service) movieStreamsArgs(ctx context.Context, tmdbID int) (plugins.StreamsArgs, error) {
	movie, err := s.EnsureTitle(ctx, MediaRef{MediaType: MediaTypeMovie, MediaID: tmdbID})
	if err != nil {
		return plugins.StreamsArgs{}, err
	}
	return plugins.StreamsArgs{
		MediaType: plugins.MediaTypeMovie, Title: movie.Title,
		OriginalTitle: movie.OriginalTitle, Year: movie.Year(),
		TMDBID: movie.TMDBID, IMDBID: movie.IMDBID,
	}, nil
}

func (s *Service) episodeStreamsArgs(ctx context.Context, tmdbID int) (plugins.StreamsArgs, error) {
	episode, err := s.Store.Episode(ctx, tmdbID)
	if err != nil {
		return plugins.StreamsArgs{}, err
	}
	if episode == nil {
		return plugins.StreamsArgs{}, fmt.Errorf("episode %d is not in the library", tmdbID)
	}
	series, err := s.EnsureTitle(ctx, MediaRef{MediaType: MediaTypeSeries, MediaID: episode.SeriesID})
	if err != nil {
		return plugins.StreamsArgs{}, err
	}
	return plugins.StreamsArgs{
		MediaType: plugins.MediaTypeEpisode, Title: series.Title,
		OriginalTitle: series.OriginalTitle, Year: series.Year(),
		TMDBID: series.TMDBID, IMDBID: series.IMDBID,
		SeasonNumber: episode.SeasonNumber, EpisodeNumber: episode.EpisodeNumber,
	}, nil
}

// StreamUpdate is the current stream list of a title; Done marks the last
// update of a lookup.
type StreamUpdate struct {
	Streams []Stream
	Done    bool
}

// Broker fans stream updates out to the subscribers of a title.
type Broker struct {
	mutex       sync.Mutex
	subscribers map[MediaRef]map[chan StreamUpdate]bool
}

func NewBroker() *Broker {
	return &Broker{subscribers: map[MediaRef]map[chan StreamUpdate]bool{}}
}

// Subscribe returns a channel of updates for ref that closes when ctx ends.
func (b *Broker) Subscribe(ctx context.Context, ref MediaRef) <-chan StreamUpdate {
	updates := make(chan StreamUpdate, 8)
	b.mutex.Lock()
	if b.subscribers[ref] == nil {
		b.subscribers[ref] = map[chan StreamUpdate]bool{}
	}
	b.subscribers[ref][updates] = true
	b.mutex.Unlock()

	go func() {
		<-ctx.Done()
		b.mutex.Lock()
		defer b.mutex.Unlock()
		delete(b.subscribers[ref], updates)
		if len(b.subscribers[ref]) == 0 {
			delete(b.subscribers, ref)
		}
		close(updates)
	}()
	return updates
}

// Publish sends an update to every subscriber of ref, skipping subscribers
// whose buffer is full rather than blocking the lookup.
func (b *Broker) Publish(ref MediaRef, update StreamUpdate) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	for subscriber := range b.subscribers[ref] {
		select {
		case subscriber <- update:
		default:
		}
	}
}

// SearchArgs describes a movie or episode the way plugins search for it. It
// fetches the title from TMDB first when it is not stored yet.
func (s *Service) SearchArgs(ctx context.Context, ref MediaRef) (plugins.StreamsArgs, error) {
	return s.streamsArgs(ctx, ref)
}
