package recommend

import "time"

// Every tunable number of the engine lives in this file so the relative
// strengths of signals, sources and penalties can be read side by side.

// Taste profile decay and signal window.
const (
	// AffinityHalfLife is how fast a signal fades: its weight halves every
	// half-life. Film taste changes slowly, so it is long.
	AffinityHalfLife = 90 * 24 * time.Hour
	// signalWindow bounds how far back signals are read; after six
	// half-lives a signal keeps under 2% of its weight.
	signalWindow = 6 * AffinityHalfLife
)

// Signal weights per title interaction, strongest positive first.
const (
	// weightRatingScale is the signal of a 10/10 rating; 1/10 gives its negative.
	weightRatingScale    = 3.0
	weightCompletedWatch = 2.0
	weightSave           = 1.5
	weightPartialWatch   = 1.0
	weightEarlyAbandon   = -1.5
	// titleWeightCap bounds one title's summed signal, so a series binged
	// episode by episode doesn't drown out everything else.
	titleWeightCap = 6.0
)

// Watch interpretation.
const (
	// ratingNeutral is the 1-10 rating that says nothing; below it is a
	// dislike that also removes the title from recommendations.
	ratingNeutral = 5.5
	// dislikeBelow is the rating under which a title counts as disliked.
	dislikeBelow = 5.0
	// A watch that stopped before either threshold is an early abandonment.
	earlyAbandonSeconds  = 5 * 60
	earlyAbandonFraction = 0.05
	// A watch that reached this share of the runtime counts as completed.
	completedFraction = 0.9
	// abandonGrace keeps a playback that just started from counting as abandoned.
	abandonGrace = 30 * time.Minute
)

// Profile and candidate generation sizes.
const (
	// leadCastSize is how many top-billed actors count as a title's cast.
	leadCastSize = 5
	// seedMinimumWeight is the decayed weight a title needs to seed related
	// candidates (a partial watch from today, a completed one from months ago).
	seedMinimumWeight = 1.0
	seedTitleLimit    = 12
	// relatedPerSeed caps each seed's related edges per source.
	relatedPerSeed        = 20
	topCastLimit          = 5
	topDirectorLimit      = 3
	topGenreLimit         = 3
	titlesPerPerson       = 15
	titlesPerGenre        = 30
	randomCandidateLimit  = 15
	popularCandidateLimit = 100
	// randomMinimumVotes keeps exploration to titles enough people rated.
	randomMinimumVotes = 500
)

// Source priors: how much a candidate source is trusted before its content
// is scored, in [0, 1]. TMDB's own recommendations are collaborative
// filtering over TMDB's whole audience, so they rank highest.
var sourcePriors = map[string]float64{
	SourceRelatedTMDB:   1.0,
	SourceRelatedPeople: 0.7,
	SourceCast:          0.65,
	SourceDirector:      0.55,
	SourceGenre:         0.45,
	SourceRandom:        0.2,
	SourcePopular:       0.1,
}

// Prior shaping.
const (
	// rankDecay lowers a candidate's prior with its position within its source.
	rankDecay = 0.05
	// seedWeightInfluence is how much a seed's strength scales the prior of
	// its related titles (0 = not at all, 1 = fully proportional).
	seedWeightInfluence = 0.5
	// multiSourceBonus is added to the prior per extra source that found the
	// same title.
	multiSourceBonus = 0.1
)

// Ranking: score = affinity + prior + quality + recency − penalties.
const (
	rankWeightAffinity    = 1.0
	rankWeightSourcePrior = 0.6
	rankWeightQuality     = 0.4
	rankWeightRecency     = 0.1
	// recencyHalfLife is the release age at which recency halves.
	recencyHalfLife = 2 * 365 * 24 * time.Hour
	// qualityVoteConfidence is the vote count at which a title's average is
	// trusted halfway; fewer votes pull the quality towards neutral.
	qualityVoteConfidence = 200.0

	penaltyWatchedPartial = 0.4
	// penaltyDislikedNeighbor hits titles a disliked title lists as related.
	penaltyDislikedNeighbor = 0.5
)

// Affinity match: how a title's features mix into one [-1, 1] value.
const (
	matchWeightCast     = 0.35
	matchWeightDirector = 0.25
	matchWeightGenre    = 0.25
	matchWeightKeyword  = 0.15
)

// Re-ranking.
const (
	// pageSize is the span the diversity caps apply to.
	pageSize              = 24
	maxPerPersonPerPage   = 2
	maxSameSourceRun      = 3
	maxSameMediaTypeRun   = 4
	explorationEvery      = 7
	feedCacheTTL          = 30 * time.Minute
	feedFirstPageTTL      = 10 * time.Minute
	minRowItems           = 4
	defaultRowItemsLimit  = 20
	defaultRowCountLimit  = 8
	defaultFeedPageLimit  = 24
	maximumFeedPageLimit  = 100
	maximumRowCountLimit  = 20
	maximumRowItemsLimit  = 50
	becauseYouWatchedRows = 3
)
