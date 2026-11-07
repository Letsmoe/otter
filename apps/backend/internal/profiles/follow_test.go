package profiles

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/library"
)

func TestBellOverridesMyListUntilTheListChanges(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	ref := library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: 1399}
	expect := func(want bool, step string) {
		t.Helper()
		got, err := service.Notifying(ctx, 1, ref)
		if err != nil {
			t.Fatal(err)
		}
		if got != want {
			t.Fatalf("%s: notifying = %v, want %v", step, got, want)
		}
	}

	expect(false, "not on My List")
	if err := service.SetNotifying(ctx, 1, ref, true); err != nil {
		t.Fatal(err)
	}
	expect(true, "bell on")
	if err := service.AddToWatchlist(ctx, 1, ref); err != nil {
		t.Fatal(err)
	}
	expect(true, "added to My List")
	if err := service.SetNotifying(ctx, 1, ref, false); err != nil {
		t.Fatal(err)
	}
	expect(false, "muted on My List")
	if err := service.RemoveFromWatchlist(ctx, 1, ref); err != nil {
		t.Fatal(err)
	}
	expect(false, "removed from My List")
}
