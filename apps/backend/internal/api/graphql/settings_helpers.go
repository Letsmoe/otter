package graphql

import (
	"context"
	"log"

	"github.com/Letsmoe/otter/internal/profiles"
	"github.com/Letsmoe/otter/internal/settings"
)

// applySettings saves a settings change. A new TMDB language marks every
// stored title as stale, so pages show the new language once opened again.
func (r *Resolver) applySettings(ctx context.Context, update settings.Update) (settings.Settings, error) {
	previousLanguage := r.Settings.Cached().TMDBLanguage
	updated, err := r.Settings.Apply(ctx, update)
	if err != nil {
		return settings.Settings{}, err
	}
	if updated.TMDBLanguage == previousLanguage {
		return updated, nil
	}
	if err := r.Library.Store.ExpireAllDetails(ctx); err != nil {
		log.Printf("settings: expiring details after a language change: %v", err)
	}
	return updated, nil
}

// adoptProfileLanguage makes the first profile's top language the TMDB
// language, so a fresh install shows titles in the language picked there.
func (r *Resolver) adoptProfileLanguage(ctx context.Context, profile profiles.Profile) error {
	languages := profile.LanguageList()
	if len(languages) == 0 {
		return nil
	}
	language := settings.TMDBLanguageFor(languages[0])
	_, err := r.applySettings(ctx, settings.Update{TMDBLanguage: &language})
	return err
}

// isFirstProfile reports whether no profile exists yet.
func (r *Resolver) isFirstProfile(ctx context.Context) (bool, error) {
	existing, err := r.Profiles.List(ctx)
	if err != nil {
		return false, err
	}
	return len(existing) == 0, nil
}
