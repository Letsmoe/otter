package library

import "testing"

func TestSearchResultsFilterByGenreAndYear(t *testing.T) {
	title := Title{Genres: []string{"Drama"}, ReleaseDate: "2015-04-01"}
	cases := []struct {
		name   string
		filter DiscoverFilter
		want   bool
	}{
		{"no filter", DiscoverFilter{}, true},
		{"matching genre", DiscoverFilter{Genre: "Drama"}, true},
		{"other genre", DiscoverFilter{Genre: "Comedy"}, false},
		{"inside the years", DiscoverFilter{MinYear: 2010, MaxYear: 2019}, true},
		{"too old", DiscoverFilter{MinYear: 2020}, false},
		{"too new", DiscoverFilter{MaxYear: 2009}, false},
	}
	for _, testCase := range cases {
		if got := matchesListFields(title, testCase.filter); got != testCase.want {
			t.Errorf("%s: got %v, want %v", testCase.name, got, testCase.want)
		}
	}
}

func TestUndatedTitlesFailAMaximumYear(t *testing.T) {
	if matchesListFields(Title{}, DiscoverFilter{MaxYear: 2000}) {
		t.Fatal("a title without a date shouldn't pass a maximum year")
	}
}

func TestNewestSortUsesEachTypesDate(t *testing.T) {
	if got := discoverSort(MediaTypeSeries, DiscoverSortNewest); got != "first_air_date.desc" {
		t.Fatalf("series newest sort %q", got)
	}
	if got := discoverSort(MediaTypeMovie, DiscoverSortNewest); got != "primary_release_date.desc" {
		t.Fatalf("movie newest sort %q", got)
	}
}
