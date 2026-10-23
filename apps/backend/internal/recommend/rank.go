package recommend

import (
	"cmp"
	"math"
	"slices"
)

// scoredItem is a ranked item plus the features re-ranking needs.
type scoredItem struct {
	Item
	features *titleFeatures
}

// scoreCandidates scores every candidate that has features, best first.
func scoreCandidates(candidates []*candidate, state *snapshot) []scoredItem {
	scored := make([]scoredItem, 0, len(candidates))
	for _, proposed := range candidates {
		title, found := state.features[proposed.media]
		if !found {
			continue
		}
		item := Item{Media: proposed.media, Source: proposed.source, Reason: proposed.reason, Score: score(proposed, title, state)}
		scored = append(scored, scoredItem{Item: item, features: title})
	}
	sortByScore(scored)
	return scored
}

// sortByScore orders items best first, ties by media so the order is stable.
func sortByScore(items []scoredItem) {
	slices.SortStableFunc(items, func(left, right scoredItem) int {
		if left.Score != right.Score {
			return cmp.Compare(right.Score, left.Score)
		}
		return cmp.Compare(refKey(left.Media), refKey(right.Media))
	})
}

// score is the transparent ranking formula: affinity match, source prior,
// quality and recency, minus the penalties.
func score(proposed *candidate, title *titleFeatures, state *snapshot) float64 {
	prior := proposed.prior + multiSourceBonus*float64(proposed.sourceCount-1)
	total := rankWeightAffinity * affinityMatch(title, state.profile)
	total += rankWeightSourcePrior * prior
	total += rankWeightQuality * quality(title)
	// An unknown release date decays to no recency at all.
	total += rankWeightRecency * decayFactor(state.now.Sub(title.released), recencyHalfLife)
	return total - penalties(proposed, state)
}

// affinityMatch mixes a title's cast, director, genre and keyword
// affinities into one value in [-1, 1].
func affinityMatch(title *titleFeatures, profile *Profile) float64 {
	match := matchWeightCast * strongestAffinity(title.cast, profile.Cast)
	match += matchWeightDirector * strongestAffinity(title.directors, profile.Directors)
	match += matchWeightGenre * dampedAffinity(title.genres, profile.Genres)
	match += matchWeightKeyword * dampedAffinity(title.keywords, profile.Keywords)
	return match
}

// strongestAffinity is the highest affinity among ids, or the lowest when
// none is positive: one loved actor carries a title, an all-disliked cast
// sinks it.
func strongestAffinity(ids []int, affinities map[int]float64) float64 {
	highest, lowest := 0.0, 0.0
	for _, id := range ids {
		highest = math.Max(highest, affinities[id])
		lowest = math.Min(lowest, affinities[id])
	}
	if highest > 0 {
		return highest
	}
	return lowest
}

// dampedAffinity sums affinities damped by the count (mirroring how the
// profile spreads weight), clamped to [-1, 1].
func dampedAffinity(names []string, affinities map[string]float64) float64 {
	if len(names) == 0 {
		return 0
	}
	sum := 0.0
	for _, name := range names {
		sum += affinities[name]
	}
	return math.Max(-1, math.Min(1, sum/math.Sqrt(float64(len(names)))))
}

// quality maps the TMDB average to [-1, 1] around 6.5, shrunk towards 0
// while few people voted.
func quality(title *titleFeatures) float64 {
	confidence := float64(title.voteCount) / (float64(title.voteCount) + qualityVoteConfidence)
	return math.Max(-1, math.Min(1, (title.voteAverage-6.5)/2.5)) * confidence
}

// penalties adds up what counts against a title: partly watched already
// (finished ones never get here), or related to a disliked title.
func penalties(proposed *candidate, state *snapshot) float64 {
	total := 0.0
	if state.watched[proposed.media] == watchPartial {
		total += penaltyWatchedPartial
	}
	if state.dislikedNeighbors[proposed.media] {
		total += penaltyDislikedNeighbor
	}
	return total
}
