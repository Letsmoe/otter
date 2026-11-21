package profiles

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/library"
)

func TestDismissAndRestoreTitle(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	heat := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 949}

	// Dismissing twice keeps one row.
	for range 2 {
		if err := service.DismissTitle(ctx, 1, heat); err != nil {
			t.Fatal(err)
		}
	}
	dismissed, err := service.DismissedTitles(ctx, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(dismissed) != 1 || dismissed[0] != heat {
		t.Fatalf("dismissed = %v, want just %v", dismissed, heat)
	}

	if err := service.RestoreTitle(ctx, 1, heat); err != nil {
		t.Fatal(err)
	}
	dismissed, err = service.DismissedTitles(ctx, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(dismissed) != 0 {
		t.Fatalf("dismissed = %v after restoring, want none", dismissed)
	}
}

func TestDeletingAProfileForgetsItsDismissedTitles(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	heat := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 949}
	if err := service.DismissTitle(ctx, created.ID, heat); err != nil {
		t.Fatal(err)
	}
	if err := service.Delete(ctx, created.ID); err != nil {
		t.Fatal(err)
	}
	dismissed, err := service.DismissedTitles(ctx, created.ID)
	if err != nil {
		t.Fatal(err)
	}
	if len(dismissed) != 0 {
		t.Fatalf("dismissed = %v after deleting the profile, want none", dismissed)
	}
}
