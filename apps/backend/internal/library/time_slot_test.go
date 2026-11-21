package library

import "testing"

func TestRemainingMinutesSubtractsWhatWasWatched(t *testing.T) {
	cases := []struct {
		runtime int
		watched float64
		want    int
	}{
		{45, 0, 45},
		{45, 600, 35},
		{45, 2699, 1},
		{45, 2800, 1},
		{0, 100, 0},
	}
	for _, testCase := range cases {
		if got := RemainingMinutes(testCase.runtime, testCase.watched); got != testCase.want {
			t.Errorf("%d min after %.0f s: got %d, want %d", testCase.runtime, testCase.watched, got, testCase.want)
		}
	}
}

func TestOnlyKnownLengthsWithinTheBudgetFit(t *testing.T) {
	if !FitsMinutes(45, 45) || !FitsMinutes(20, 45) {
		t.Error("45 and 20 minutes should fit 45")
	}
	if FitsMinutes(46, 45) || FitsMinutes(0, 45) {
		t.Error("46 minutes and an unknown length should not fit 45")
	}
}

func TestFilmsThatFitLeavesOutSeriesAndLongFilmsAndUsesTheTimeBest(t *testing.T) {
	titles := []Title{
		{MediaType: MediaTypeMovie, TMDBID: 1, Runtime: 80},
		{MediaType: MediaTypeMovie, TMDBID: 2, Runtime: 95},
		{MediaType: MediaTypeMovie, TMDBID: 3, Runtime: 0},
		{MediaType: MediaTypeSeries, TMDBID: 4, Runtime: 30},
		{MediaType: MediaTypeMovie, TMDBID: 5, Runtime: 140},
		{MediaType: MediaTypeMovie, TMDBID: 6, Runtime: 60},
	}

	got := FilmsThatFit(titles, 100)

	want := []int{2, 1, 6}
	if len(got) != len(want) {
		t.Fatalf("got %+v", got)
	}
	for index, id := range want {
		if got[index].TMDBID != id {
			t.Errorf("place %d: got %d, want %d", index, got[index].TMDBID, id)
		}
	}
}
