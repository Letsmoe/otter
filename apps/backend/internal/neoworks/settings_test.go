package neoworks

import (
	"context"
	"slices"
	"testing"

	"github.com/Letsmoe/otter/internal/settings"
)

// householdOf gives a device a settings service and records what the registry is told.
func householdOf(t *testing.T, phone device) (*settings.Service, *[]string) {
	t.Helper()
	household := settings.NewService(phone.database)
	if err := household.Init(context.Background(), ""); err != nil {
		t.Fatal(err)
	}
	disabled := []string{}
	return household, &disabled
}

func syncSettings(t *testing.T, phone device, household *settings.Service, disabled *[]string) (int, int) {
	t.Helper()
	mirror := settingsMirror{
		database: phone.database, store: phone.mirror.store, settings: household,
		restoreDisabled: func(names []string) { *disabled = names },
	}
	received, sent, err := mirror.sync(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	return received, sent
}

func TestSettingsAreWrittenOnceAndThenLeftAlone(t *testing.T) {
	phone := newDevice(t)
	household, disabled := householdOf(t, phone)
	language := "de-DE"
	if _, err := household.Apply(context.Background(), settings.Update{
		TMDBLanguage: &language, DisabledPlugins: []string{"s-to"},
		PluginSettings: map[string]map[string]string{"kinoking": {"OTTER_BASE_URL": "https://kinoking.cc"}},
	}); err != nil {
		t.Fatal(err)
	}
	if _, sent := syncSettings(t, phone, household, disabled); sent != 1 {
		t.Fatalf("sent %d, want the settings row", sent)
	}
	if received, sent := syncSettings(t, phone, household, disabled); received != 0 || sent != 0 {
		t.Fatalf("unchanged settings: received %d, sent %d", received, sent)
	}
}

func TestSettingsChangedElsewhereApplyHereWithTheirPlugins(t *testing.T) {
	phone := newDevice(t)
	household, disabled := householdOf(t, phone)
	syncSettings(t, phone, household, disabled)

	rows, _, err := phone.mirror.store.rows(tableSettings)
	if err != nil || len(rows) != 1 {
		t.Fatalf("%d settings rows, %v", len(rows), err)
	}
	for rowID := range rows {
		changes := map[string]any{"tmdbLanguage": "fr-FR", "disabledPlugins": []string{"movie2k"}, "updatedAt": at(0)}
		if err := phone.mirror.store.updateRow(rowID, changes); err != nil {
			t.Fatal(err)
		}
	}
	if received, _ := syncSettings(t, phone, household, disabled); received != 1 {
		t.Fatalf("received %d, want the changed settings", received)
	}
	current := household.Cached()
	if current.TMDBLanguage != "fr-FR" || !slices.Equal(current.DisabledPlugins, []string{"movie2k"}) {
		t.Fatalf("settings here: %+v", current)
	}
	if !slices.Equal(*disabled, []string{"movie2k"}) {
		t.Fatalf("registry told %v, want movie2k switched off", *disabled)
	}
}
