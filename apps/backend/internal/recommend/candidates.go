package recommend

import (
	"context"
	"fmt"
	"strconv"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

// candidate is a title some source proposes, before scoring.
type candidate struct {
	media  library.MediaRef
	source string
	// prior is the source's trust in this title, in [0, 1].
	prior float64
	// sourceCount is how many sources proposed the title.
	sourceCount int
	reason      Reason
}

// candidatePool merges candidates by title, keeping the strongest source's
// prior and reason and counting how many sources agreed.
type candidatePool struct {
	byMedia map[library.MediaRef]*candidate
	order   []library.MediaRef
}

func newCandidatePool() *candidatePool {
	return &candidatePool{byMedia: map[library.MediaRef]*candidate{}}
}

func (pool *candidatePool) add(proposed candidate) {
	existing, found := pool.byMedia[proposed.media]
	if !found {
		proposed.sourceCount = 1
		pool.byMedia[proposed.media] = &proposed
		pool.order = append(pool.order, proposed.media)
		return
	}
	if existing.source != proposed.source {
		existing.sourceCount++
	}
	if proposed.prior > existing.prior {
		existing.prior = proposed.prior
		existing.source = proposed.source
		existing.reason = proposed.reason
	}
}

// list returns the pooled candidates in first-seen order.
func (pool *candidatePool) list() []*candidate {
	candidates := make([]*candidate, 0, len(pool.order))
	for _, ref := range pool.order {
		candidates = append(candidates, pool.byMedia[ref])
	}
	return candidates
}

type candidateGenerator func(ctx context.Context, state *snapshot) ([]candidate, error)

// generateCandidates runs every source and pools the results.
func (e *Engine) generateCandidates(ctx context.Context, state *snapshot) (*candidatePool, error) {
	generators := []candidateGenerator{
		e.relatedCandidates,
		e.personCandidates,
		e.genreCandidates,
		e.randomCandidates,
		e.popularCandidates,
	}
	pool := newCandidatePool()
	for _, generate := range generators {
		found, err := generate(ctx, state)
		if err != nil {
			return nil, err
		}
		for _, proposed := range found {
			pool.add(proposed)
		}
	}
	return pool, nil
}

// rankedPrior lowers a source prior by the item's position within the source.
func rankedPrior(prior float64, rank int) float64 {
	return prior / (1 + float64(rank)*rankDecay)
}

// refCandidates turns a ranked title list from one source into candidates.
func refCandidates(refs []library.MediaRef, source string, prior float64, reason Reason) []candidate {
	candidates := make([]candidate, 0, len(refs))
	for rank, ref := range refs {
		candidates = append(candidates, candidate{media: ref, source: source, prior: rankedPrior(prior, rank), reason: reason})
	}
	return candidates
}

func (e *Engine) refs(ctx context.Context, query string, vars db.Vars) ([]library.MediaRef, error) {
	refs, err := db.QueryAs[library.MediaRef](ctx, e.database, query, vars)
	if err != nil {
		return nil, fmt.Errorf("candidate query: %w", err)
	}
	return refs, nil
}

type relatedRow struct {
	MediaType   library.MediaType `json:"media_type"`
	MediaID     int               `json:"media_id"`
	RelatedType library.MediaType `json:"related_type"`
	RelatedID   int               `json:"related_id"`
	Source      string            `json:"source"`
	Rank        int               `json:"rank"`
}

// relatedCandidates proposes the related titles of the seeds. A stronger
// seed lends its related titles a higher prior.
func (e *Engine) relatedCandidates(ctx context.Context, state *snapshot) ([]candidate, error) {
	seeds := state.profile.Seeds
	if len(seeds) == 0 {
		return nil, nil
	}
	seedsByMedia := make(map[library.MediaRef]Seed, len(seeds))
	refs := make([]library.MediaRef, 0, len(seeds))
	for _, seed := range seeds {
		seedsByMedia[seed.Media] = seed
		refs = append(refs, seed.Media)
	}
	rows, err := db.QueryAs[relatedRow](ctx, e.database, `SELECT * FROM related
		WHERE ((media_type = 'movie' AND media_id IN (SELECT value FROM json_each($movies)))
			OR (media_type = 'series' AND media_id IN (SELECT value FROM json_each($series))))
		AND rank < $per_seed ORDER BY rank`,
		db.Vars{"movies": idsOf(refs, library.MediaTypeMovie), "series": idsOf(refs, library.MediaTypeSeries), "per_seed": relatedPerSeed})
	if err != nil {
		return nil, fmt.Errorf("related candidates: %w", err)
	}
	candidates := make([]candidate, 0, len(rows))
	for _, row := range rows {
		seed := seedsByMedia[library.MediaRef{MediaType: row.MediaType, MediaID: row.MediaID}]
		candidates = append(candidates, relatedCandidate(row, seed, seeds[0].Weight))
	}
	return candidates, nil
}

func relatedCandidate(row relatedRow, seed Seed, strongestSeed float64) candidate {
	source := "related:" + row.Source
	seedFactor := 1 - seedWeightInfluence + seedWeightInfluence*seed.Weight/strongestSeed
	return candidate{
		media:  library.MediaRef{MediaType: row.RelatedType, MediaID: row.RelatedID},
		source: source,
		prior:  rankedPrior(sourcePriors[source], row.Rank) * seedFactor,
		reason: Reason{Kind: ReasonRelated, EntityID: refKey(seed.Media), EntityName: seed.Title},
	}
}

// personTitlesQuery lists a person's best-rated titles.
const personTitlesQuery = `SELECT DISTINCT credit.media_type, credit.media_id FROM credit
	LEFT JOIN movie ON credit.media_type = 'movie' AND movie.tmdb_id = credit.media_id
	LEFT JOIN series ON credit.media_type = 'series' AND series.tmdb_id = credit.media_id
	WHERE credit.person_id = $person
	ORDER BY COALESCE(movie.vote_average * movie.vote_count, series.vote_average * series.vote_count, 0) DESC
	LIMIT $limit`

// personCandidates proposes the best titles of the top-affinity actors and
// directors, each person's prior scaled by their affinity.
func (e *Engine) personCandidates(ctx context.Context, state *snapshot) ([]candidate, error) {
	dimensions := []struct {
		source     string
		affinities map[int]float64
		limit      int
	}{
		{SourceCast, state.profile.Cast, topCastLimit},
		{SourceDirector, state.profile.Directors, topDirectorLimit},
	}
	candidates := []candidate{}
	for _, dimension := range dimensions {
		for _, personID := range TopEntities(dimension.affinities, dimension.limit) {
			refs, err := e.refs(ctx, personTitlesQuery, db.Vars{"person": personID, "limit": titlesPerPerson})
			if err != nil {
				return nil, err
			}
			prior := sourcePriors[dimension.source] * dimension.affinities[personID]
			reason := Reason{Kind: ReasonPerson, EntityID: strconv.Itoa(personID)}
			candidates = append(candidates, refCandidates(refs, dimension.source, prior, reason)...)
		}
	}
	return candidates, nil
}

const genreTitlesQuery = `SELECT media_type, media_id FROM (
	SELECT 'movie' AS media_type, tmdb_id AS media_id, popularity FROM movie
	WHERE EXISTS (SELECT 1 FROM json_each(movie.genres) WHERE value = $genre)
	UNION ALL
	SELECT 'series', tmdb_id, popularity FROM series
	WHERE EXISTS (SELECT 1 FROM json_each(series.genres) WHERE value = $genre)
) ORDER BY popularity DESC LIMIT $limit`

// genreCandidates proposes popular titles of the top-affinity genres.
func (e *Engine) genreCandidates(ctx context.Context, state *snapshot) ([]candidate, error) {
	candidates := []candidate{}
	for _, genre := range TopEntities(state.profile.Genres, topGenreLimit) {
		refs, err := e.refs(ctx, genreTitlesQuery, db.Vars{"genre": genre, "limit": titlesPerGenre})
		if err != nil {
			return nil, err
		}
		prior := sourcePriors[SourceGenre] * state.profile.Genres[genre]
		reason := Reason{Kind: ReasonGenre, EntityID: genre, EntityName: genre}
		candidates = append(candidates, refCandidates(refs, SourceGenre, prior, reason)...)
	}
	return candidates, nil
}

// randomCandidates proposes a few random well-known titles once there is a
// profile to escape from.
func (e *Engine) randomCandidates(ctx context.Context, state *snapshot) ([]candidate, error) {
	if state.profile.Empty() {
		return nil, nil
	}
	refs, err := e.refs(ctx, `SELECT media_type, media_id FROM (
		SELECT 'movie' AS media_type, tmdb_id AS media_id FROM movie WHERE vote_count >= $votes
		UNION ALL SELECT 'series', tmdb_id FROM series WHERE vote_count >= $votes
	) ORDER BY random() LIMIT $limit`, db.Vars{"votes": randomMinimumVotes, "limit": randomCandidateLimit})
	if err != nil {
		return nil, err
	}
	return refCandidates(refs, SourceRandom, sourcePriors[SourceRandom], Reason{Kind: ReasonRandom}), nil
}

// popularCandidates proposes the most popular known titles: the cold-start
// fallback and the feed's tail.
func (e *Engine) popularCandidates(ctx context.Context, state *snapshot) ([]candidate, error) {
	refs, err := e.refs(ctx, `SELECT media_type, media_id FROM (
		SELECT 'movie' AS media_type, tmdb_id AS media_id, popularity FROM movie
		UNION ALL SELECT 'series', tmdb_id, popularity FROM series
	) ORDER BY popularity DESC LIMIT $limit`, db.Vars{"limit": popularCandidateLimit})
	if err != nil {
		return nil, err
	}
	return refCandidates(refs, SourcePopular, sourcePriors[SourcePopular], Reason{Kind: ReasonPopular}), nil
}
