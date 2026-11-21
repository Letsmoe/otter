package graphql

import (
	"time"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/yearinreview"
)

const secondsPerHour = 3600.0

// yearInReviewModel maps a review; highlights whose title is no longer stored are left out.
func yearInReviewModel(result yearinreview.Result, offsetMinutes int) *model.YearInReview {
	review := result.Review
	mapper := reviewMapper{titles: result.Titles, location: time.FixedZone("profile", offsetMinutes*60)}
	return &model.YearInReview{
		Year:             review.Year,
		HasActivity:      review.HasActivity,
		HoursWatched:     review.WatchedSeconds / secondsPerHour,
		MoviesFinished:   review.MoviesFinished,
		EpisodesFinished: review.EpisodesFinished,
		SeriesWatched:    review.SeriesWatched,
		TopSeries:        mapper.topSeries(review.TopSeries),
		TopGenres:        genreShareModels(review.TopGenres),
		LongestBinge:     mapper.binge(review.LongestBinge),
		MostRewatched:    mapper.rewatched(review.MostRewatched),
		FirstTitle:       mapper.moment(review.First),
		LastTitle:        mapper.moment(review.Last),
	}
}

type reviewMapper struct {
	titles   map[library.MediaRef]library.Title
	location *time.Location
}

func (mapper reviewMapper) title(ref library.MediaRef) *model.Title {
	title, found := mapper.titles[ref]
	if !found {
		return nil
	}
	return titleModel(title)
}

func seriesTitleRef(seriesID int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: seriesID}
}

func (mapper reviewMapper) topSeries(stat *yearinreview.SeriesStat) *model.TopSeries {
	if stat == nil {
		return nil
	}
	title := mapper.title(seriesTitleRef(stat.SeriesID))
	if title == nil {
		return nil
	}
	return &model.TopSeries{Title: title, HoursWatched: stat.Seconds / secondsPerHour, EpisodesWatched: stat.Episodes}
}

func (mapper reviewMapper) binge(binge *yearinreview.Binge) *model.Binge {
	if binge == nil {
		return nil
	}
	title := mapper.title(seriesTitleRef(binge.SeriesID))
	if title == nil {
		return nil
	}
	return &model.Binge{Title: title, Episodes: binge.Episodes, Date: binge.Night}
}

func (mapper reviewMapper) rewatched(rewatched *yearinreview.Rewatched) *model.Rewatched {
	if rewatched == nil {
		return nil
	}
	title := mapper.title(rewatched.Title)
	if title == nil {
		return nil
	}
	return &model.Rewatched{Title: title, Count: rewatched.Count}
}

func (mapper reviewMapper) moment(moment *yearinreview.Moment) *model.YearMoment {
	if moment == nil {
		return nil
	}
	title := mapper.title(moment.Title)
	if title == nil {
		return nil
	}
	return &model.YearMoment{Title: title, Date: moment.At.In(mapper.location).Format("2006-01-02")}
}

func genreShareModels(stats []yearinreview.GenreStat) []*model.GenreShare {
	models := make([]*model.GenreShare, 0, len(stats))
	for _, stat := range stats {
		models = append(models, &model.GenreShare{Genre: stat.Name, HoursWatched: stat.Seconds / secondsPerHour, Share: stat.Share})
	}
	return models
}
