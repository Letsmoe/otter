package library

import (
	"context"
	"fmt"
	"log"
	"slices"
	"strings"
	"time"

	"github.com/Letsmoe/otter/internal/plugins"
)

const (
	// Running anime get new episodes, so the list is read again after a week.
	fillerTTL = 7 * 24 * time.Hour
	// Not finding the show is not likely to change soon, but a site outage is.
	emptyFillerTTL = 24 * time.Hour
	// One plugin's filler lookup is two pages of one site.
	fillerPluginTimeout = 40 * time.Second
)

// fillerFollowUp makes sure, in the background, that the series' episodes carry their filler marks.
func (s *Service) fillerFollowUp(seriesID int) {
	s.inBackground(fmt.Sprintf("filler of series %d", seriesID), func(ctx context.Context) error {
		return s.EnsureFiller(ctx, seriesID)
	})
}

// EnsureFiller asks the filler plugins about an animated series when they weren't asked lately and
// marks its episodes with what they answer. Other series are left alone.
func (s *Service) EnsureFiller(ctx context.Context, seriesID int) error {
	key := fmt.Sprintf("filler:%d", seriesID)
	_, err, _ := s.inflight.Do(key, func() (any, error) {
		return nil, s.ensureFiller(ctx, seriesID)
	})
	return err
}

func (s *Service) ensureFiller(ctx context.Context, seriesID int) error {
	title, err := s.Store.Title(ctx, MediaRef{MediaType: MediaTypeSeries, MediaID: seriesID})
	if err != nil || title == nil || !isAnimation(title) {
		return err
	}
	stored, err := s.Store.Filler(ctx, seriesID)
	if err != nil {
		return err
	}
	if stored == nil || fillerIsStale(stored) {
		if err := s.fetchFiller(ctx, title); err != nil {
			return err
		}
	}
	return s.applyFiller(ctx, seriesID)
}

func isAnimation(title *Title) bool {
	return slices.ContainsFunc(title.Genres, func(genre string) bool {
		return strings.EqualFold(genre, "Animation")
	})
}

func fillerIsStale(stored *StoredFiller) bool {
	if len(stored.Entries) == 0 {
		return isStale(stored.FetchedAt, emptyFillerTTL)
	}
	return isStale(stored.FetchedAt, fillerTTL)
}

// fetchFiller keeps the first plugin answer that has episodes; none having any is kept as well.
func (s *Service) fetchFiller(ctx context.Context, title *Title) error {
	if err := s.streamsAllowed(); err != nil {
		return err
	}
	args := plugins.FillerArgs{
		Title: title.Title, OriginalTitle: title.OriginalTitle, Year: title.Year(),
		TMDBID: title.TMDBID, IMDBID: title.IMDBID,
	}
	var entries []plugins.FillerEpisode
	for _, plugin := range s.plugins.WithCapability(plugins.CapabilityFiller) {
		entries = s.askFillerPlugin(ctx, plugin, args)
		if len(entries) > 0 {
			break
		}
	}
	return s.Store.SaveFiller(ctx, title.TMDBID, entries)
}

func (s *Service) askFillerPlugin(ctx context.Context, plugin *plugins.Plugin, args plugins.FillerArgs) []plugins.FillerEpisode {
	pluginContext, cancel := context.WithTimeout(ctx, fillerPluginTimeout)
	defer cancel()
	result, err := s.runner.Filler(pluginContext, plugin, args)
	if err != nil {
		log.Printf("filler: %s for %q: %v", plugin.Meta.Name, args.Title, err)
		plugins.ReportFailure(ctx, err, plugin, plugins.CapabilityFiller, map[string]any{"query": args})
		return nil
	}
	return result.Episodes
}

// applyFiller marks the series' episodes from the stored list, once every season's episodes are
// stored: the list's numbers count across seasons, so a missing season would shift all after it.
func (s *Service) applyFiller(ctx context.Context, seriesID int) error {
	stored, err := s.Store.Filler(ctx, seriesID)
	if err != nil || stored == nil {
		return err
	}
	complete, err := s.allSeasonsStored(ctx, seriesID)
	if err != nil || !complete {
		return err
	}
	episodes, err := s.Store.SeriesEpisodes(ctx, seriesID)
	if err != nil {
		return err
	}
	return s.Store.SetEpisodeFiller(ctx, seriesID, mapFillerToEpisodes(stored.Entries, episodes))
}

func (s *Service) allSeasonsStored(ctx context.Context, seriesID int) (bool, error) {
	seasons, err := s.Store.Seasons(ctx, seriesID)
	if err != nil {
		return false, err
	}
	for _, season := range seasons {
		if season.SeasonNumber > 0 && season.DetailFetchedAt == "" {
			return false, nil
		}
	}
	return len(seasons) > 0, nil
}
