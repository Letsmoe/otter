package graphql

import (
	"context"
	"errors"
	"fmt"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/resolve"
)

// candidateLinks are the stored links of the given stream ids, best host first.
type candidateLinks struct {
	urls     []string
	idsByURL map[string]int
	// The movies and episodes the links belong to, to announce new verdicts.
	media map[library.MediaRef]bool
}

// candidateLinks loads the links and orders them by how their hosts did
// lately, so a race starts with the historically best hosts.
func (r *mutationResolver) candidateLinks(ctx context.Context, streamIDs []string) (candidateLinks, error) {
	stored, err := r.storedLinks(ctx, streamIDs)
	if err != nil {
		return candidateLinks{}, err
	}
	stats, err := r.Library.Store.AllHosterStats(ctx)
	if err != nil {
		return candidateLinks{}, err
	}
	candidates := candidateLinks{idsByURL: map[string]int{}, media: map[library.MediaRef]bool{}}
	for _, link := range library.RankByHoster(stored, stats) {
		candidates.idsByURL[link.URL] = link.ID
		candidates.urls = append(candidates.urls, link.URL)
		candidates.media[library.MediaRef{MediaType: link.MediaType, MediaID: link.MediaID}] = true
	}
	return candidates, nil
}

// storedLinks loads the given streams in order, once per URL.
func (r *mutationResolver) storedLinks(ctx context.Context, streamIDs []string) ([]library.Stream, error) {
	var links []library.Stream
	seen := map[string]bool{}
	for _, streamID := range streamIDs {
		id, err := parseStream(streamID)
		if err != nil {
			return nil, err
		}
		stored, err := r.Library.Store.Stream(ctx, id)
		if err != nil || stored == nil {
			return nil, fmt.Errorf("stream %s not found", streamID)
		}
		if seen[stored.URL] {
			continue
		}
		seen[stored.URL] = true
		links = append(links, *stored)
	}
	return links, nil
}

// resolveBest plays nothing that wasn't proven to deliver video: every link
// is resolved and probed, the outcomes are stored, and only the winner starts
// caching.
func (r *mutationResolver) resolveBest(ctx context.Context, streamIDs []string) (*model.ResolvedStream, error) {
	if err := r.requireVpnIfConfigured(); err != nil {
		return nil, err
	}
	candidates, err := r.candidateLinks(ctx, streamIDs)
	if err != nil {
		return nil, err
	}
	winner, err := r.StreamResolver.ResolveBest(ctx, candidates.urls, r.StreamProxy, r.recordOutcome)
	// Show the verdicts, speeds and resolutions in the open source lists.
	for ref := range candidates.media {
		r.Library.AnnounceStreams(context.Background(), ref)
	}
	if errors.Is(err, resolve.ErrNothingPlays) {
		return nil, errors.New("none of these sources plays right now")
	}
	if err != nil {
		return nil, err
	}
	if !winner.Cached {
		r.StreamResolver.StartCaching(winner.URL, winner.Result)
	}
	playableURL, err := r.StreamProxy.ManifestURL(ctx, winner.URL, winner.Result)
	if err != nil {
		return nil, err
	}
	resolved := &model.ResolvedStream{URL: playableURL, MimeType: winner.Result.MimeType, Quality: optional(winner.Result.Quality)}
	resolved.StreamID = optional(formatID(kindStream, candidates.idsByURL[winner.URL]))
	return resolved, nil
}

// recordOutcome remembers whether a link played and how fast, for ranking;
// a dead link is removed. Attempts cut short by a finished race say nothing
// about the link.
func (r *mutationResolver) recordOutcome(outcome resolve.Outcome) {
	if errors.Is(outcome.Err, context.Canceled) {
		return
	}
	if plugins.IsDeadLink(outcome.Err) {
		_ = r.Library.Store.ForgetDeadLink(context.Background(), outcome.URL)
		_ = r.Library.Store.RecordHosterOutcome(context.Background(), outcome.URL, false, 0)
		return
	}
	played := outcome.Err == nil
	_ = r.Library.Store.MarkPlayable(context.Background(), outcome.URL, played, outcome.BytesPerSecond, outcome.Quality)
	_ = r.Library.Store.RecordHosterOutcome(context.Background(), outcome.URL, played, outcome.BytesPerSecond)
}
