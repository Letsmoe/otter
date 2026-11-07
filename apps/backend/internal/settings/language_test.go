package settings

import "testing"

func TestTMDBLanguageFor(t *testing.T) {
	cases := map[string]string{"de": "de-DE", "en": "en-US", "pt": "pt-BR", "sv": "sv"}
	for languageCode, want := range cases {
		if got := TMDBLanguageFor(languageCode); got != want {
			t.Errorf("TMDBLanguageFor(%q) = %q, want %q", languageCode, got, want)
		}
	}
}
