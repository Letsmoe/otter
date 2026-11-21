package tmdb

import (
	"encoding/json"
	"testing"
)

func TestMovieCertificationsPreferTheTheatricalRelease(t *testing.T) {
	body := `{"results":[
		{"iso_3166_1":"DE","release_dates":[{"certification":"16","type":4},{"certification":"12","type":3}]},
		{"iso_3166_1":"US","release_dates":[{"certification":"","type":3},{"certification":"R","type":4}]},
		{"iso_3166_1":"FR","release_dates":[{"certification":"","type":3}]}]}`
	var response releaseDatesResponse
	if err := json.Unmarshal([]byte(body), &response); err != nil {
		t.Fatal(err)
	}

	got := movieCertifications(response)

	want := []Certification{{"DE", "12"}, {"US", "R"}}
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestSeriesCertificationsSkipEmptyRatings(t *testing.T) {
	body := `{"results":[{"iso_3166_1":"DE","rating":"6"},{"iso_3166_1":"US","rating":""}]}`
	var response contentRatingsResponse
	if err := json.Unmarshal([]byte(body), &response); err != nil {
		t.Fatal(err)
	}

	got := seriesCertifications(response)

	if len(got) != 1 || got[0] != (Certification{"DE", "6"}) {
		t.Fatalf("got %+v", got)
	}
}
