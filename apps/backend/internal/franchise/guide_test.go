package franchise

import (
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/library"
)

var today = time.Date(2026, 11, 21, 12, 0, 0, 0, time.UTC)

func film(id int, releaseDate string) library.Title {
	return library.Title{MediaType: library.MediaTypeMovie, TMDBID: id, ReleaseDate: releaseDate}
}

func trilogy() []library.Title {
	return []library.Title{film(1, "1999-03-30"), film(2, "2003-05-15"), film(3, "2003-11-05")}
}

func TestStatusOfAFilm(t *testing.T) {
	cases := []struct {
		name     string
		watch    Watch
		status   Status
		progress float64
	}{
		{"never opened", Watch{}, StatusNotYet, 0},
		{"flagged completed", Watch{Completed: true, ProgressSeconds: 100, DurationSeconds: 7200}, StatusWatched, 1},
		{"watched to the credits", Watch{ProgressSeconds: 6600, DurationSeconds: 7200}, StatusWatched, 1},
		{"half way", Watch{ProgressSeconds: 3600, DurationSeconds: 7200}, StatusInProgress, 0.5},
	}
	for _, testCase := range cases {
		status, progress := statusOf(testCase.watch)
		if status != testCase.status || progress != testCase.progress {
			t.Errorf("%s: got (%s, %v), want (%s, %v)", testCase.name, status, progress, testCase.status, testCase.progress)
		}
	}
}

func TestGuideNumbersFilmsInTheOrderGiven(t *testing.T) {
	guide := NewGuide(library.Collection{Name: "Trilogy"}, trilogy(), nil, today)
	for index, part := range guide.Parts {
		if part.Number != index+1 || part.Title.TMDBID != index+1 {
			t.Fatalf("part %d = %+v", index, part)
		}
	}
	if guide.Started() || guide.WatchedCount() != 0 {
		t.Fatal("an untouched guide has no progress")
	}
}

func TestNextIsTheFirstFilmNotFinished(t *testing.T) {
	watches := map[int]Watch{1: {Completed: true}, 2: {ProgressSeconds: 600, DurationSeconds: 7200}}
	guide := NewGuide(library.Collection{}, trilogy(), watches, today)

	next := guide.Next()
	if next == nil || next.Title.TMDBID != 2 || next.Status != StatusInProgress {
		t.Fatalf("next = %+v, want the film in progress", next)
	}
	if guide.WatchedCount() != 1 || !guide.Started() {
		t.Fatalf("watched = %d, started = %v", guide.WatchedCount(), guide.Started())
	}
}

func TestNextSkipsFilmsNotOutYet(t *testing.T) {
	titles := []library.Title{film(1, "2020-01-01"), film(2, "2027-05-01"), film(3, "")}
	guide := NewGuide(library.Collection{}, titles, map[int]Watch{1: {Completed: true}}, today)
	if next := guide.Next(); next != nil {
		t.Fatalf("next = %+v, want none while the rest is unreleased", next)
	}
	if guide.Parts[1].Released || guide.Parts[2].Released {
		t.Fatal("future and undated films are not released")
	}
}

func TestNextWhenNothingWatchedIsTheFirstFilm(t *testing.T) {
	guide := NewGuide(library.Collection{}, trilogy(), nil, today)
	next := guide.Next()
	if next == nil || next.Number != 1 {
		t.Fatalf("next = %+v, want the first film", next)
	}
}

func TestGuidesSortInProgressThenUntouchedThenFinished(t *testing.T) {
	finished := NewGuide(library.Collection{Name: "finished"}, trilogy(), map[int]Watch{1: {Completed: true}, 2: {Completed: true}, 3: {Completed: true}}, today)
	untouched := NewGuide(library.Collection{Name: "untouched"}, trilogy(), nil, today)
	started := NewGuide(library.Collection{Name: "started"}, trilogy(), map[int]Watch{1: {Completed: true}}, today)
	if compareGuides(started, untouched) >= 0 || compareGuides(untouched, finished) >= 0 {
		t.Fatal("want started before untouched before finished")
	}
}
