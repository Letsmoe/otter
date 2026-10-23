package profiles

import (
	"context"
	"errors"
	"path/filepath"
	"testing"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/hooks"
)

func newTestService(t *testing.T) *Service {
	t.Helper()
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	return NewService(database, cache.New(ctx, database), hooks.New(t.TempDir(), nil))
}

func TestUpdateChangesOnlyGivenFields(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "avatar:blue", "", false)
	if err != nil {
		t.Fatal(err)
	}

	newName := "Franky"
	updated, err := service.Update(ctx, created.ID, Update{Name: &newName})
	if err != nil {
		t.Fatal(err)
	}
	if updated.Name != "Franky" || updated.AvatarURL != "avatar:blue" || updated.HasPin() {
		t.Fatalf("unexpected profile after rename: %+v", updated)
	}
}

func TestUpdateSetsAndRemovesPin(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}

	pin := "1234"
	locked, err := service.Update(ctx, created.ID, Update{Pin: &pin})
	if err != nil {
		t.Fatal(err)
	}
	if !locked.HasPin() {
		t.Fatal("expected a PIN after setting one")
	}
	check, err := service.VerifyPin(ctx, created.ID, "1234")
	if err != nil || !check.Success {
		t.Fatalf("expected the new PIN to verify, got %+v, %v", check, err)
	}

	noPin := ""
	unlocked, err := service.Update(ctx, created.ID, Update{Pin: &noPin})
	if err != nil {
		t.Fatal(err)
	}
	if unlocked.HasPin() {
		t.Fatal("expected the PIN to be removed")
	}
}

func TestDuplicateNameIsReportedAsTaken(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	if _, err := service.Create(ctx, "Frank", "", "", false); err != nil {
		t.Fatal(err)
	}
	_, err := service.Create(ctx, "Frank", "", "", false)
	if !errors.Is(err, ErrNameTaken) {
		t.Fatalf("expected ErrNameTaken, got %v", err)
	}
}

func TestLanguagesDefaultAndUpdate(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Ana", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	if got := created.LanguageList(); len(got) != 2 || got[0] != "de" || got[1] != "en" {
		t.Fatalf("default languages = %v, want [de en]", got)
	}

	languages := []string{" EN ", "de", "en"}
	updated, err := service.Update(ctx, created.ID, Update{Languages: &languages})
	if err != nil {
		t.Fatal(err)
	}
	if got := updated.LanguageList(); len(got) != 2 || got[0] != "en" || got[1] != "de" {
		t.Fatalf("languages = %v, want [en de]", got)
	}
}

func TestLanguagesRejectsInvalidCodes(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Ben", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	for _, languages := range [][]string{{}, {"german"}, {"de-sub"}} {
		if _, err := service.Update(ctx, created.ID, Update{Languages: &languages}); err == nil {
			t.Errorf("languages %v were accepted", languages)
		}
	}
}

func TestCaptionPreferencesDefaultAndUpdate(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	if created.SubtitlesEnabled || created.VoiceBoost || created.SubtitleSize != SubtitleSizeMedium || created.SubtitleLanguage != "" {
		t.Fatalf("unexpected caption defaults: %+v", created)
	}
	if created.CaptionLanguage() != "de" {
		t.Errorf("caption language falls back to the first audio language, got %q", created.CaptionLanguage())
	}

	enabled, size, language := true, SubtitleSizeLarge, "EN"
	updated, err := service.Update(ctx, created.ID, Update{SubtitlesEnabled: &enabled, SubtitleSize: &size, SubtitleLanguage: &language})
	if err != nil {
		t.Fatal(err)
	}
	if !updated.SubtitlesEnabled || updated.SubtitleSize != SubtitleSizeLarge || updated.CaptionLanguage() != "en" {
		t.Fatalf("unexpected profile after caption update: %+v", updated)
	}

	cleared := ""
	updated, err = service.Update(ctx, created.ID, Update{SubtitleLanguage: &cleared})
	if err != nil {
		t.Fatal(err)
	}
	if updated.SubtitleLanguage != "" || !updated.SubtitlesEnabled || updated.CaptionLanguage() != "de" {
		t.Fatalf("clearing the language should leave other fields alone: %+v", updated)
	}
}

func TestCaptionPreferencesRejectInvalidValues(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	badSize, badLanguage := "huge", "deu"
	if _, err := service.Update(ctx, created.ID, Update{SubtitleSize: &badSize}); err == nil {
		t.Error("expected an error for an unknown caption size")
	}
	if _, err := service.Update(ctx, created.ID, Update{SubtitleLanguage: &badLanguage}); err == nil {
		t.Error("expected an error for a three-letter language code")
	}
}

func TestVoiceBoostUpdate(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	boost := true
	updated, err := service.Update(ctx, created.ID, Update{VoiceBoost: &boost})
	if err != nil {
		t.Fatal(err)
	}
	if !updated.VoiceBoost {
		t.Fatalf("voice boost should be on: %+v", updated)
	}
}
