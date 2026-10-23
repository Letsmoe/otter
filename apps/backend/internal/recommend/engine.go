// Package recommend is the recommendation engine:
// candidate generation from cheap local sources (TMDB related edges, the
// filmographies of favourite people, favourite genres), a transparent
// weighted ranking against a time-decayed per-profile taste profile, and
// diversity-aware re-ranking. Everything runs as a handful of indexed SQLite
// queries per ranking pass, so it works embedded on a phone.
//
// The taste profile is computed on demand rather than stored: signals are
// one row per user action, so the aggregation stays small and there is no
// cache to invalidate on every rating or progress write. The ranked feed is
// memoized per profile for pagination.
package recommend

import (
	"cmp"
	"context"
	"fmt"
	"slices"
	"strconv"
	"sync"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

// Engine ranks recommendations. Safe for concurrent use.
type Engine struct {
	database *db.DB
	// now is the clock, swappable in tests.
	now func() time.Time

	mutex sync.Mutex
	feeds map[int]*rankedFeed
}

// rankedFeed is one full ranking pass for one profile.
type rankedFeed struct {
	builtAt time.Time
	// scored holds every eligible candidate, best score first.
	scored []scoredItem
	// ranked is the re-ranked feed order.
	ranked []Item
}

func New(database *db.DB) *Engine {
	return &Engine{database: database, now: time.Now, feeds: map[int]*rankedFeed{}}
}

// Feed returns one page of a profile's ranked feed, optionally only one
// media type. Offset 0 re-ranks once the ranking is older than
// feedFirstPageTTL, or always when refresh is set; later pages read the same
// ranking, so pages neither overlap nor skip.
func (e *Engine) Feed(ctx context.Context, profileID int, mediaType library.MediaType, limit, offset int, refresh bool) ([]Item, error) {
	maxAge := feedCacheTTL
	if offset == 0 {
		maxAge = feedFirstPageTTL
	}
	if refresh {
		maxAge = 0
	}
	feed, err := e.rankedFeed(ctx, profileID, maxAge)
	if err != nil {
		return nil, err
	}
	return page(filterType(feed.ranked, mediaType), limit, offset), nil
}

// Rows groups the ranked candidates by reason into titled rows ("Because
// you watched X", "More with <actor>", ...), strongest row first.
func (e *Engine) Rows(ctx context.Context, profileID int, rowLimit, perRow int) ([]Row, error) {
	feed, err := e.rankedFeed(ctx, profileID, feedCacheTTL)
	if err != nil {
		return nil, err
	}
	return groupRows(feed.scored, rowLimit, perRow), nil
}

// Forget drops a profile's memoized feed, e.g. after a rating, so the next
// request re-ranks.
func (e *Engine) Forget(profileID int) {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	delete(e.feeds, profileID)
}

func (e *Engine) rankedFeed(ctx context.Context, profileID int, maxAge time.Duration) (*rankedFeed, error) {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	cached := e.feeds[profileID]
	if cached != nil && e.now().Sub(cached.builtAt) < maxAge {
		return cached, nil
	}
	feed, err := e.buildFeed(ctx, profileID)
	if err != nil {
		return nil, err
	}
	e.feeds[profileID] = feed
	return feed, nil
}

// buildFeed runs the full pipeline: snapshot, candidates, scoring, reasons,
// re-ranking.
func (e *Engine) buildFeed(ctx context.Context, profileID int) (*rankedFeed, error) {
	state, err := e.takeSnapshot(ctx, profileID)
	if err != nil {
		return nil, err
	}
	pool, err := e.generateCandidates(ctx, state)
	if err != nil {
		return nil, err
	}
	candidates := state.eligible(pool.list())
	if err := e.addFeatures(ctx, state, candidates); err != nil {
		return nil, err
	}
	scored := scoreCandidates(candidates, state)
	scored = withoutAdult(scored)
	if err := e.describeReasons(ctx, scored); err != nil {
		return nil, err
	}
	return &rankedFeed{builtAt: state.now, scored: scored, ranked: rerank(scored)}, nil
}

// snapshot is everything one ranking pass reads about a profile, taken once
// so every candidate is scored against the same state.
type snapshot struct {
	now               time.Time
	profile           *Profile
	watched           map[library.MediaRef]watchState
	dislikedNeighbors map[library.MediaRef]bool
	features          map[library.MediaRef]*titleFeatures
}

func (e *Engine) takeSnapshot(ctx context.Context, profileID int) (*snapshot, error) {
	now := e.now()
	signals, err := e.collectSignals(ctx, profileID, now)
	if err != nil {
		return nil, err
	}
	features, err := e.loadFeatures(ctx, signalRefs(signals))
	if err != nil {
		return nil, err
	}
	profile := buildProfile(signals, features, now)
	watched, err := e.watchStates(ctx, profileID)
	if err != nil {
		return nil, err
	}
	neighbors, err := e.dislikedNeighbors(ctx, profile.Disliked)
	if err != nil {
		return nil, err
	}
	return &snapshot{now: now, profile: profile, watched: watched, dislikedNeighbors: neighbors, features: features}, nil
}

// eligible drops disliked titles, which must never come back, and movies
// the profile already finished or rated.
func (state *snapshot) eligible(candidates []*candidate) []*candidate {
	kept := make([]*candidate, 0, len(candidates))
	for _, proposed := range candidates {
		disliked := state.profile.Disliked[proposed.media]
		seen := state.watched[proposed.media] == watchCompleted
		if !disliked && !seen {
			kept = append(kept, proposed)
		}
	}
	return kept
}

// addFeatures loads the features of candidates not already known.
func (e *Engine) addFeatures(ctx context.Context, state *snapshot, candidates []*candidate) error {
	missing := []library.MediaRef{}
	for _, proposed := range candidates {
		if _, known := state.features[proposed.media]; !known {
			missing = append(missing, proposed.media)
		}
	}
	loaded, err := e.loadFeatures(ctx, missing)
	if err != nil {
		return err
	}
	for ref, features := range loaded {
		state.features[ref] = features
	}
	return nil
}

func (e *Engine) dislikedNeighbors(ctx context.Context, disliked map[library.MediaRef]bool) (map[library.MediaRef]bool, error) {
	neighbors := map[library.MediaRef]bool{}
	if len(disliked) == 0 {
		return neighbors, nil
	}
	refs := make([]library.MediaRef, 0, len(disliked))
	for ref := range disliked {
		refs = append(refs, ref)
	}
	rows, err := e.refs(ctx, `SELECT related_type AS media_type, related_id AS media_id FROM related
		WHERE (media_type = 'movie' AND media_id IN (SELECT value FROM json_each($movies)))
		OR (media_type = 'series' AND media_id IN (SELECT value FROM json_each($series)))`,
		db.Vars{"movies": idsOf(refs, library.MediaTypeMovie), "series": idsOf(refs, library.MediaTypeSeries)})
	if err != nil {
		return nil, err
	}
	for _, ref := range rows {
		neighbors[ref] = true
	}
	return neighbors, nil
}

func signalRefs(signals []signal) []library.MediaRef {
	seen := map[library.MediaRef]bool{}
	refs := []library.MediaRef{}
	for _, entry := range signals {
		if !seen[entry.media] {
			seen[entry.media] = true
			refs = append(refs, entry.media)
		}
	}
	return refs
}

func withoutAdult(scored []scoredItem) []scoredItem {
	return slices.DeleteFunc(scored, func(item scoredItem) bool { return item.features.adult })
}

// describeReasons resolves person names behind the reasons and writes
// their text.
func (e *Engine) describeReasons(ctx context.Context, items []scoredItem) error {
	names, err := e.personNames(ctx, items)
	if err != nil {
		return err
	}
	for index := range items {
		reason := &items[index].Reason
		if reason.Kind == ReasonPerson {
			reason.EntityName = names[reason.EntityID]
		}
		reason.describe()
	}
	return nil
}

func (e *Engine) personNames(ctx context.Context, items []scoredItem) (map[string]string, error) {
	ids := []int{}
	for _, item := range items {
		if item.Reason.Kind != ReasonPerson {
			continue
		}
		id, err := strconv.Atoi(item.Reason.EntityID)
		if err == nil {
			ids = append(ids, id)
		}
	}
	names := map[string]string{}
	if len(ids) == 0 {
		return names, nil
	}
	rows, err := e.database.Query(ctx, `SELECT tmdb_id, name FROM person
		WHERE tmdb_id IN (SELECT value FROM json_each($ids))`, db.Vars{"ids": ids})
	if err != nil {
		return nil, fmt.Errorf("person names: %w", err)
	}
	for _, row := range rows {
		names[strconv.Itoa(db.AsInt(row["tmdb_id"]))] = fmt.Sprint(row["name"])
	}
	return names, nil
}

// groupRows buckets the best-first items by reason, drops thin and
// popular-only rows, keeps at most becauseYouWatchedRows seed rows, and
// orders rows by the mean score of their top three.
func groupRows(scored []scoredItem, rowLimit, perRow int) []Row {
	rows := []Row{}
	seedRows := 0
	for _, row := range bucketByReason(scored) {
		if row.Reason.Kind == ReasonPopular || len(row.Items) < minRowItems {
			continue
		}
		if row.Reason.Kind == ReasonRelated {
			if seedRows >= becauseYouWatchedRows {
				continue
			}
			seedRows++
		}
		rows = append(rows, row)
	}
	slices.SortStableFunc(rows, func(left, right Row) int {
		return cmp.Compare(rowStrength(right), rowStrength(left))
	})
	return trimRows(rows, rowLimit, perRow)
}

func bucketByReason(scored []scoredItem) []Row {
	indexByKey := map[string]int{}
	rows := []Row{}
	for _, item := range scored {
		key := item.Reason.Key()
		index, found := indexByKey[key]
		if !found {
			index = len(rows)
			indexByKey[key] = index
			rows = append(rows, Row{Key: key, Title: item.Reason.Text, Reason: item.Reason})
		}
		rows[index].Items = append(rows[index].Items, item.Item)
	}
	return rows
}

// rowStrength is the mean score of a row's top three items.
func rowStrength(row Row) float64 {
	count := min(3, len(row.Items))
	sum := 0.0
	for _, item := range row.Items[:count] {
		sum += item.Score
	}
	return sum / float64(count)
}

func trimRows(rows []Row, rowLimit, perRow int) []Row {
	if len(rows) > rowLimit {
		rows = rows[:rowLimit]
	}
	for index := range rows {
		if len(rows[index].Items) > perRow {
			rows[index].Items = rows[index].Items[:perRow]
		}
	}
	return rows
}

// filterType keeps the items of one media type; an empty type keeps all.
func filterType(items []Item, mediaType library.MediaType) []Item {
	if mediaType == "" {
		return items
	}
	kept := []Item{}
	for _, item := range items {
		if item.Media.MediaType == mediaType {
			kept = append(kept, item)
		}
	}
	return kept
}

func page(items []Item, limit, offset int) []Item {
	if offset >= len(items) || limit <= 0 {
		return []Item{}
	}
	end := min(offset+limit, len(items))
	return slices.Clone(items[offset:end])
}
