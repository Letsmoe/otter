package library

import "testing"

func TestAgeRatingLabelNamesGermanRatingsFSK(t *testing.T) {
	cases := []struct{ country, certification, want string }{
		{"DE", "12", "FSK 12"},
		{"de", "0", "FSK 0"},
		{"US", "PG-13", "PG-13"},
		{"DE", "Freigegeben ohne Altersbeschränkung", "Freigegeben ohne Altersbeschränkung"},
		{"GB", " 15 ", "15"},
		{"DE", "", ""},
	}
	for _, testCase := range cases {
		if got := AgeRatingLabel(testCase.country, testCase.certification); got != testCase.want {
			t.Errorf("AgeRatingLabel(%q, %q) = %q, want %q", testCase.country, testCase.certification, got, testCase.want)
		}
	}
}
