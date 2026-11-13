package settings

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
)

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

func newTestService(t *testing.T) *Service {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	return NewService(database)
}

func TestFreshInstallRequiresVpn(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	if err := service.Init(ctx, ""); err != nil {
		t.Fatal(err)
	}
	if !service.Cached().RequireVpn {
		t.Fatal("a fresh install should require a VPN")
	}
}

func TestSavedVpnChoiceSurvivesRestart(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	if err := service.Init(ctx, ""); err != nil {
		t.Fatal(err)
	}
	requireVpn := false
	if _, err := service.Apply(ctx, Update{RequireVpn: &requireVpn}); err != nil {
		t.Fatal(err)
	}

	restarted := NewService(service.database)
	if err := restarted.Init(ctx, ""); err != nil {
		t.Fatal(err)
	}
	if restarted.Cached().RequireVpn {
		t.Fatal("a saved 'no VPN' choice was overridden by the default")
	}
}

func TestErrorReportingIsOnByDefaultAndCanBeTurnedOff(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	if err := service.Init(ctx, ""); err != nil {
		t.Fatal(err)
	}
	if !service.Cached().ErrorReporting {
		t.Fatal("a fresh install should report errors")
	}
	errorReporting := false
	if _, err := service.Apply(ctx, Update{ErrorReporting: &errorReporting}); err != nil {
		t.Fatal(err)
	}

	restarted := NewService(service.database)
	if err := restarted.Init(ctx, ""); err != nil {
		t.Fatal(err)
	}
	if restarted.Cached().ErrorReporting {
		t.Fatal("turning error reporting off didn't survive a restart")
	}
}
