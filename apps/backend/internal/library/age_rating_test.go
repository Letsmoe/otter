package library

import "testing"

func TestMinimumAgeReadsEachCountrysScale(t *testing.T) {
	cases := []struct {
		country, certification string
		age                    int
		known                  bool
	}{
		{"DE", "12", 12, true},
		{"DE", "0", 0, true},
		{"US", "PG-13", 13, true},
		{"US", "TV-MA", 17, true},
		{"GB", "12A", 12, true},
		{"de", " 16 ", 16, true},
		// Not in the table, but a number says it.
		{"SE", "11", 11, true},
		{"XX", "FSK 16", 16, true},
		{"DE", "Not Rated", 0, false},
		{"DE", "", 0, false},
		{"US", "NR", 0, false},
		// Not an age.
		{"XX", "2024", 0, false},
	}
	for _, testCase := range cases {
		age, known := MinimumAge(testCase.country, testCase.certification)
		if age != testCase.age || known != testCase.known {
			t.Errorf("%s %q: got %d, %v; want %d, %v", testCase.country, testCase.certification, age, known, testCase.age, testCase.known)
		}
	}
}

func TestCountryForFollowsTheProfilesLanguage(t *testing.T) {
	cases := []struct {
		languages []string
		tmdb      string
		country   string
	}{
		{[]string{"de", "en"}, "de-DE", "DE"},
		{[]string{"de"}, "de-AT", "AT"},
		{[]string{"de"}, "en-US", "DE"},
		{[]string{"en"}, "de-DE", "US"},
		{[]string{}, "fr-FR", "FR"},
		{[]string{"ja"}, "de-DE", ""},
	}
	for _, testCase := range cases {
		if got := CountryFor(testCase.languages, testCase.tmdb); got != testCase.country {
			t.Errorf("%v %q: got %q, want %q", testCase.languages, testCase.tmdb, got, testCase.country)
		}
	}
}
