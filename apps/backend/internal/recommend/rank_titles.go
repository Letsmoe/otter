package recommend

import (
	"context"

	"github.com/neoworks/otter/internal/library"
)

// Titles handed in from outside (e.g. one person's filmography) all get the
// same middling source prior, so only the profile's taste orders them.
const (
	givenTitlesSource = "given"
	givenTitlesPrior  = 0.5
)

// RankTitles orders refs by how well they fit the profile, best first, with
// the feed's own formula. Disliked titles, finished movies and adult titles
// are left out, like in the feed.
func (e *Engine) RankTitles(ctx context.Context, profileID int, refs []library.MediaRef) ([]library.MediaRef, error) {
	state, err := e.takeSnapshot(ctx, profileID)
	if err != nil {
		return nil, err
	}
	candidates := make([]*candidate, 0, len(refs))
	for _, ref := range refs {
		candidates = append(candidates, &candidate{media: ref, source: givenTitlesSource, prior: givenTitlesPrior, sourceCount: 1})
	}
	candidates = state.eligible(candidates)
	if err := e.addFeatures(ctx, state, candidates); err != nil {
		return nil, err
	}
	scored := withoutAdult(scoreCandidates(candidates, state))
	ranked := make([]library.MediaRef, 0, len(scored))
	for _, item := range scored {
		ranked = append(ranked, item.Media)
	}
	return ranked, nil
}
