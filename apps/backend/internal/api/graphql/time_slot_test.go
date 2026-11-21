package graphql

import (
	"testing"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
)

func intPointer(value int) *int { return &value }

func episodeEntry(runtime int, watchedSeconds float64) *model.ContinueItem {
	entry := &model.ContinueItem{
		Title:   &model.Title{ID: "series:1", MediaType: model.MediaTypeSeries},
		Episode: &model.Episode{ID: "episode:7", Runtime: intPointer(runtime)},
	}
	if watchedSeconds > 0 {
		entry.Progress = &model.WatchProgress{ProgressSeconds: watchedSeconds}
	}
	return entry
}

func TestAnEpisodeThatFitsIsOfferedAsTheNextOne(t *testing.T) {
	items := continuingThatFits([]*model.ContinueItem{episodeEntry(42, 0)}, 45)

	if len(items) != 1 || items[0].Minutes != 42 || items[0].Reason != "Next episode" {
		t.Fatalf("got %+v", items)
	}
}

func TestAnEpisodeThatIsTooLongFitsOnceMostOfItIsWatched(t *testing.T) {
	entry := episodeEntry(60, 40*60)

	if items := continuingThatFits([]*model.ContinueItem{episodeEntry(60, 0)}, 30); len(items) != 0 {
		t.Fatalf("a fresh 60 minute episode fits 30 minutes: %+v", items)
	}
	items := continuingThatFits([]*model.ContinueItem{entry}, 30)
	if len(items) != 1 || items[0].Minutes != 20 || items[0].Reason != "Pick up where you stopped" {
		t.Fatalf("got %+v", items)
	}
}

func TestAMovieInProgressUsesItsRuntime(t *testing.T) {
	movie := &model.ContinueItem{
		Title:    &model.Title{ID: "movie:603", MediaType: model.MediaTypeMovie, Runtime: intPointer(136)},
		Progress: &model.WatchProgress{ProgressSeconds: 90 * 60},
	}

	items := continuingThatFits([]*model.ContinueItem{movie}, 60)

	if len(items) != 1 || items[0].Minutes != 46 {
		t.Fatalf("got %+v", items)
	}
}

func TestTitlesWithoutARuntimeAreNotOffered(t *testing.T) {
	unknown := &model.ContinueItem{Title: &model.Title{ID: "movie:1", MediaType: model.MediaTypeMovie}}

	if items := continuingThatFits([]*model.ContinueItem{unknown}, 90); len(items) != 0 {
		t.Fatalf("got %+v", items)
	}
}
