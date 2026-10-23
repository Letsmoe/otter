package settings

import "testing"

func TestTMDBFallsBackToDefaultKey(t *testing.T) {
	previous := DefaultTMDBApiKey
	DefaultTMDBApiKey = "built-in"
	t.Cleanup(func() { DefaultTMDBApiKey = previous })

	service := &Service{}
	service.store(Settings{TMDBLanguage: "de-DE"})
	if key, _ := service.TMDB(); key != "built-in" || !service.UsesDefaultTMDBKey() {
		t.Fatalf("without own key: key %q, uses default %v", key, service.UsesDefaultTMDBKey())
	}

	service.store(Settings{TMDBApiKey: "own", TMDBLanguage: "de-DE"})
	if key, _ := service.TMDB(); key != "own" || service.UsesDefaultTMDBKey() {
		t.Fatalf("with own key: key %q, uses default %v", key, service.UsesDefaultTMDBKey())
	}
}
