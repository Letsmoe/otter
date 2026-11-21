package library

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/tmdb"
)

func TestCertificationsAreStoredPerCountryAndMarkedFetched(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	rated := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	unrated := MediaRef{MediaType: MediaTypeSeries, MediaID: 1396}
	never := MediaRef{MediaType: MediaTypeMovie, MediaID: 1}
	if err := store.SaveCertifications(ctx, rated, []tmdb.Certification{{Country: "DE", Rating: "16"}, {Country: "US", Rating: "R"}}); err != nil {
		t.Fatal(err)
	}
	if err := store.SaveCertifications(ctx, unrated, nil); err != nil {
		t.Fatal(err)
	}

	stored, err := store.Certifications(ctx, []MediaRef{rated, unrated, never})
	if err != nil {
		t.Fatal(err)
	}

	if !stored[rated].Fetched || stored[rated].ByCountry["DE"] != "16" || stored[rated].ByCountry["US"] != "R" {
		t.Errorf("rated: %+v", stored[rated])
	}
	if !stored[unrated].Fetched || len(stored[unrated].ByCountry) != 0 {
		t.Errorf("unrated: %+v", stored[unrated])
	}
	if stored[never].Fetched {
		t.Errorf("never asked: %+v", stored[never])
	}
}

func TestSavingCertificationsAgainReplacesTheOldOnes(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	_ = store.SaveCertifications(ctx, ref, []tmdb.Certification{{Country: "DE", Rating: "16"}})
	_ = store.SaveCertifications(ctx, ref, []tmdb.Certification{{Country: "DE", Rating: "12"}})

	stored, err := store.Certifications(ctx, []MediaRef{ref})
	if err != nil {
		t.Fatal(err)
	}

	if stored[ref].ByCountry["DE"] != "12" {
		t.Fatalf("got %+v", stored[ref])
	}
}

func TestOnlyReadableRatingsWithinTheLimitAreAllowed(t *testing.T) {
	limit := AgeLimit{Age: 12, Country: "DE"}
	cases := []struct {
		name         string
		certificates StoredCertifications
		allowed      bool
	}{
		{"younger", StoredCertifications{Fetched: true, ByCountry: map[string]string{"DE": "6"}}, true},
		{"exactly the limit", StoredCertifications{Fetched: true, ByCountry: map[string]string{"DE": "12"}}, true},
		{"older", StoredCertifications{Fetched: true, ByCountry: map[string]string{"DE": "16"}}, false},
		{"rated elsewhere only", StoredCertifications{Fetched: true, ByCountry: map[string]string{"US": "G"}}, false},
		{"unreadable", StoredCertifications{Fetched: true, ByCountry: map[string]string{"DE": "Not Rated"}}, false},
		{"never fetched", StoredCertifications{}, false},
	}
	for _, testCase := range cases {
		if got := withinLimit(testCase.certificates, limit); got != testCase.allowed {
			t.Errorf("%s: got %v, want %v", testCase.name, got, testCase.allowed)
		}
	}
}
