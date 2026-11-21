package letterboxd

import (
	"context"
	"strconv"
	"testing"

	"github.com/Letsmoe/otter/internal/tmdb"
)

type fakeFinder struct {
	// answers by "title|year|language".
	answers map[string][]tmdb.SearchResult
	asked   []string
}

func (f *fakeFinder) SearchMovieByTitle(_ context.Context, title string, year int, language string) ([]tmdb.SearchResult, error) {
	key := title + "|" + yearText(year) + "|" + language
	f.asked = append(f.asked, key)
	return f.answers[key], nil
}

func yearText(year int) string {
	if year == 0 {
		return "any"
	}
	return strconv.Itoa(year)
}

func movie(id int, title, original, released string, popularity float64) tmdb.SearchResult {
	return tmdb.SearchResult{ID: id, Title: title, OriginalTitle: original, ReleaseDate: released, Popularity: popularity}
}

func TestTitlesCompareWithoutAccentsPunctuationOrCase(t *testing.T) {
	same := [][2]string{
		{"Amélie", "Amelie"},
		{"Se7en", "SE7EN"},
		{"Fast & Furious", "Fast and Furious"},
		{"Mission: Impossible - Fallout", "mission impossible fallout"},
		{"  The   Thing ", "the thing"},
	}
	for _, pair := range same {
		if normalizeTitle(pair[0]) != normalizeTitle(pair[1]) {
			t.Errorf("%q and %q should compare equal: %q vs %q", pair[0], pair[1], normalizeTitle(pair[0]), normalizeTitle(pair[1]))
		}
	}
	if normalizeTitle("Heat") == normalizeTitle("Heat 2") {
		t.Error("different titles compared equal")
	}
}

func TestMatchTakesTheFilmOfTheRightYear(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"The Thing|1982|": {
			movie(2, "The Thing", "The Thing", "2011-10-12", 90),
			movie(1, "The Thing", "The Thing", "1982-06-25", 40),
		},
	}}
	result, ok, err := Match(context.Background(), finder, Film{Name: "The Thing", Year: 1982})
	if err != nil || !ok || result.ID != 1 {
		t.Fatalf("matched %+v ok=%v err=%v", result, ok, err)
	}
}

func TestMatchAllowsAYearEitherSide(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Heat|1995|": {movie(949, "Heat", "Heat", "1996-01-15", 50)},
	}}
	result, ok, _ := Match(context.Background(), finder, Film{Name: "Heat", Year: 1995})
	if !ok || result.ID != 949 {
		t.Fatalf("matched %+v ok=%v", result, ok)
	}
}

func TestMatchRefusesAFilmFromAnotherDecade(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Heat|1995|": {movie(1, "Heat", "Heat", "1986-01-01", 50)},
	}}
	if _, ok, _ := Match(context.Background(), finder, Film{Name: "Heat", Year: 1995}); ok {
		t.Fatal("a film nine years off was matched")
	}
}

func TestMatchFindsAFilmThroughItsOriginalTitle(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Parasite|2019|": {movie(496243, "Parasit", "기생충", "2019-05-30", 80), movie(7, "Parasite", "Parasite", "2019-01-01", 5)},
	}}
	result, ok, _ := Match(context.Background(), finder, Film{Name: "Parasite", Year: 2019})
	if !ok || result.ID != 7 {
		t.Fatalf("matched %+v", result)
	}
}

func TestMatchFallsBackToTheEnglishTitleThenToNoYear(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"The Godfather|1972|en-US": {movie(238, "The Godfather", "The Godfather", "1972-03-14", 99)},
	}}
	result, ok, _ := Match(context.Background(), finder, Film{Name: "The Godfather", Year: 1972})
	if !ok || result.ID != 238 {
		t.Fatalf("matched %+v ok=%v, asked %v", result, ok, finder.asked)
	}
	if len(finder.asked) != 2 {
		t.Errorf("asked %v", finder.asked)
	}

	yearOff := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Solaris|any|en-US": {movie(593, "Solaris", "Солярис", "1972-03-20", 30)},
	}}
	if _, ok, _ := Match(context.Background(), yearOff, Film{Name: "Solaris", Year: 1972}); !ok {
		t.Fatalf("no match, asked %v", yearOff.asked)
	}
}

func TestMatchWithoutAYearTakesTheMostPopular(t *testing.T) {
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{
		"Heat|any|": {movie(1, "Heat", "Heat", "1986-01-01", 5), movie(2, "Heat", "Heat", "1995-12-15", 70)},
	}}
	result, ok, _ := Match(context.Background(), finder, Film{Name: "Heat"})
	if !ok || result.ID != 2 {
		t.Fatalf("matched %+v", result)
	}
}

func TestMatchSkipsAdultResults(t *testing.T) {
	adult := movie(1, "Heat", "Heat", "1995-12-15", 70)
	adult.Adult = true
	finder := &fakeFinder{answers: map[string][]tmdb.SearchResult{"Heat|1995|": {adult}}}
	if _, ok, _ := Match(context.Background(), finder, Film{Name: "Heat", Year: 1995}); ok {
		t.Fatal("an adult result was matched")
	}
}
