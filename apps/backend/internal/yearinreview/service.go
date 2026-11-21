package yearinreview

import (
	"context"
	"fmt"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

// Service reads a profile's history and builds its review.
type Service struct {
	database *db.DB
	store    *library.Store
}

func NewService(database *db.DB, store *library.Store) *Service {
	return &Service{database: database, store: store}
}

// Result is a review with the titles it mentions.
type Result struct {
	Review Review
	Titles map[library.MediaRef]library.Title
}

type activityRow struct {
	MediaType       library.MediaType `json:"media_type"`
	MediaID         int               `json:"media_id"`
	SeriesID        int               `json:"series_id"`
	ProgressSeconds float64           `json:"progress_seconds"`
	DurationSeconds float64           `json:"duration_seconds"`
	Completed       bool              `json:"completed"`
	StartedAt       string            `json:"started_at"`
	UpdatedAt       string            `json:"updated_at"`
}

// Year builds the profile's review of a year. utcOffsetMinutes is the phone's
// time zone, so days and nights fall where the profile lived them.
func (s *Service) Year(ctx context.Context, profileID, year, utcOffsetMinutes int) (*Result, error) {
	activities, err := s.activities(ctx, profileID)
	if err != nil {
		return nil, err
	}
	location := time.FixedZone("profile", utcOffsetMinutes*60)
	genres, err := s.genresOf(ctx, activities)
	if err != nil {
		return nil, err
	}
	review := Build(activities, genres, year, location)
	titles, err := s.titlesOf(ctx, review)
	if err != nil {
		return nil, err
	}
	return &Result{Review: review, Titles: titles}, nil
}

func (s *Service) activities(ctx context.Context, profileID int) ([]Activity, error) {
	rows, err := db.QueryAs[activityRow](ctx, s.database, `SELECT watch_history.media_type, watch_history.media_id,
		COALESCE(episode.series_id, 0) AS series_id, watch_history.progress_seconds,
		watch_history.duration_seconds, watch_history.completed, watch_history.started_at, watch_history.updated_at
		FROM watch_history LEFT JOIN episode ON watch_history.media_type = 'episode'
			AND episode.tmdb_id = watch_history.media_id
		WHERE watch_history.profile_id = $profile_id`, db.Vars{"profile_id": profileID})
	if err != nil {
		return nil, fmt.Errorf("read watch history: %w", err)
	}
	activities := make([]Activity, 0, len(rows))
	for _, row := range rows {
		activities = append(activities, row.activity())
	}
	return activities, nil
}

func (row activityRow) activity() Activity {
	startedAt, _ := time.Parse(db.TimeFormat, row.StartedAt)
	updatedAt, _ := time.Parse(db.TimeFormat, row.UpdatedAt)
	return Activity{
		Media:           library.MediaRef{MediaType: row.MediaType, MediaID: row.MediaID},
		SeriesID:        row.SeriesID,
		ProgressSeconds: row.ProgressSeconds,
		DurationSeconds: row.DurationSeconds,
		Completed:       row.Completed,
		StartedAt:       startedAt,
		UpdatedAt:       updatedAt,
	}
}

// genresOf loads the genres of the titles the activities belong to.
func (s *Service) genresOf(ctx context.Context, activities []Activity) (map[library.MediaRef][]string, error) {
	refs := distinctTitles(activities)
	titles, err := s.store.Titles(ctx, refs)
	if err != nil {
		return nil, err
	}
	genres := make(map[library.MediaRef][]string, len(titles))
	for _, title := range titles {
		genres[title.Ref()] = title.Genres
	}
	return genres, nil
}

func distinctTitles(activities []Activity) []library.MediaRef {
	seen := map[library.MediaRef]bool{}
	refs := []library.MediaRef{}
	for _, activity := range activities {
		ref := activity.Title()
		if !seen[ref] {
			seen[ref] = true
			refs = append(refs, ref)
		}
	}
	return refs
}

// titlesOf loads the titles the review's highlights point at.
func (s *Service) titlesOf(ctx context.Context, review Review) (map[library.MediaRef]library.Title, error) {
	titles, err := s.store.Titles(ctx, highlightedTitles(review))
	if err != nil {
		return nil, err
	}
	byRef := make(map[library.MediaRef]library.Title, len(titles))
	for _, title := range titles {
		byRef[title.Ref()] = title
	}
	return byRef, nil
}

func highlightedTitles(review Review) []library.MediaRef {
	refs := []library.MediaRef{}
	if review.TopSeries != nil {
		refs = append(refs, seriesRef(review.TopSeries.SeriesID))
	}
	if review.LongestBinge != nil {
		refs = append(refs, seriesRef(review.LongestBinge.SeriesID))
	}
	if review.MostRewatched != nil {
		refs = append(refs, review.MostRewatched.Title)
	}
	if review.First != nil {
		refs = append(refs, review.First.Title)
	}
	if review.Last != nil {
		refs = append(refs, review.Last.Title)
	}
	return refs
}

func seriesRef(seriesID int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: seriesID}
}
