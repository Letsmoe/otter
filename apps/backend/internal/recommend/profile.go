package recommend

import (
	"cmp"
	"math"
	"slices"
	"time"

	"github.com/neoworks/otter/internal/library"
)

// Profile is a profile's time-decayed taste: an affinity in [-1, 1] per
// actor, director, genre and keyword, normalized per dimension so the
// strongest entity of each is ±1.
type Profile struct {
	Cast      map[int]float64
	Directors map[int]float64
	Genres    map[string]float64
	Keywords  map[string]float64
	// Seeds are the titles with the strongest positive signal, strongest first.
	Seeds []Seed
	// Disliked holds the titles rated below dislikeBelow.
	Disliked    map[library.MediaRef]bool
	SignalCount int
}

// Seed is a title whose related titles are worth recommending.
type Seed struct {
	Media  library.MediaRef
	Title  string
	Weight float64
}

// Empty reports whether there is no signal yet (cold start).
func (profile *Profile) Empty() bool {
	return profile.SignalCount == 0
}

func newProfile(signalCount int) *Profile {
	return &Profile{
		Cast:        map[int]float64{},
		Directors:   map[int]float64{},
		Genres:      map[string]float64{},
		Keywords:    map[string]float64{},
		Disliked:    map[library.MediaRef]bool{},
		SignalCount: signalCount,
	}
}

// decayFactor is the share of a signal's weight left after age.
func decayFactor(age, halfLife time.Duration) float64 {
	if age <= 0 {
		return 1
	}
	return math.Pow(0.5, float64(age)/float64(halfLife))
}

// buildProfile turns signals into per-entity affinities.
func buildProfile(signals []signal, features map[library.MediaRef]*titleFeatures, now time.Time) *Profile {
	profile := newProfile(len(signals))
	titleWeights := decayedTitleWeights(signals, now)
	for ref, weight := range titleWeights {
		if title, found := features[ref]; found {
			profile.addTitle(title, weight)
		}
	}
	for _, affinities := range []map[int]float64{profile.Cast, profile.Directors} {
		normalizeAffinities(affinities)
	}
	for _, affinities := range []map[string]float64{profile.Genres, profile.Keywords} {
		normalizeAffinities(affinities)
	}
	profile.Disliked = dislikedTitles(signals)
	profile.Seeds = seedsFrom(titleWeights, features)
	return profile
}

// decayedTitleWeights sums each title's signals after time decay, capped.
func decayedTitleWeights(signals []signal, now time.Time) map[library.MediaRef]float64 {
	weights := map[library.MediaRef]float64{}
	for _, entry := range signals {
		weights[entry.media] += entry.weight * decayFactor(now.Sub(entry.at), AffinityHalfLife)
	}
	for ref, weight := range weights {
		weights[ref] = math.Max(-titleWeightCap, math.Min(titleWeightCap, weight))
	}
	return weights
}

func dislikedTitles(signals []signal) map[library.MediaRef]bool {
	disliked := map[library.MediaRef]bool{}
	for _, entry := range signals {
		if entry.disliked {
			disliked[entry.media] = true
		}
	}
	return disliked
}

// addTitle spreads a title's weight onto its entities. Genres and keywords
// share the weight (damped by the square root of their count) so a heavily
// tagged title doesn't swamp the dimension; people take it whole.
func (profile *Profile) addTitle(title *titleFeatures, weight float64) {
	for _, person := range title.cast {
		profile.Cast[person] += weight
	}
	for _, person := range title.directors {
		profile.Directors[person] += weight
	}
	addDamped(profile.Genres, title.genres, weight)
	addDamped(profile.Keywords, title.keywords, weight)
}

func addDamped(affinities map[string]float64, names []string, weight float64) {
	if len(names) == 0 {
		return
	}
	share := weight / math.Sqrt(float64(len(names)))
	for _, name := range names {
		affinities[name] += share
	}
}

// normalizeAffinities divides every value by the largest magnitude.
func normalizeAffinities[Key comparable](affinities map[Key]float64) {
	largest := 0.0
	for _, value := range affinities {
		largest = math.Max(largest, math.Abs(value))
	}
	if largest == 0 {
		return
	}
	for key, value := range affinities {
		affinities[key] = value / largest
	}
}

// seedsFrom picks the titles strong enough to seed related candidates,
// strongest first.
func seedsFrom(titleWeights map[library.MediaRef]float64, features map[library.MediaRef]*titleFeatures) []Seed {
	seeds := []Seed{}
	for ref, weight := range titleWeights {
		title, found := features[ref]
		if !found || weight < seedMinimumWeight {
			continue
		}
		seeds = append(seeds, Seed{Media: ref, Title: title.title, Weight: weight})
	}
	slices.SortFunc(seeds, func(left, right Seed) int {
		if left.Weight != right.Weight {
			return cmp.Compare(right.Weight, left.Weight)
		}
		return cmp.Compare(refKey(left.Media), refKey(right.Media))
	})
	if len(seeds) > seedTitleLimit {
		seeds = seeds[:seedTitleLimit]
	}
	return seeds
}

// TopEntities returns up to limit keys with a positive value, highest first
// (ties by key, so the order is stable).
func TopEntities[Key cmp.Ordered](values map[Key]float64, limit int) []Key {
	keys := make([]Key, 0, len(values))
	for key, value := range values {
		if value > 0 {
			keys = append(keys, key)
		}
	}
	slices.SortFunc(keys, func(left, right Key) int {
		if values[left] != values[right] {
			return cmp.Compare(values[right], values[left])
		}
		return cmp.Compare(left, right)
	})
	if len(keys) > limit {
		keys = keys[:limit]
	}
	return keys
}
