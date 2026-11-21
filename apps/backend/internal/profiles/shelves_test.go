package profiles

import (
	"context"
	"errors"
	"slices"
	"strings"
	"testing"

	"github.com/Letsmoe/otter/internal/library"
)

func movieRef(id int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: id}
}

func newShelf(t *testing.T, service *Service, profileID int, name string) *Shelf {
	t.Helper()
	shelf, err := service.CreateShelf(context.Background(), profileID, name)
	if err != nil {
		t.Fatal(err)
	}
	return shelf
}

func shelfNames(shelves []Shelf) []string {
	names := make([]string, 0, len(shelves))
	for _, shelf := range shelves {
		names = append(names, shelf.Name)
	}
	return names
}

func TestShelvesKeepTheirCreationOrderPerProfile(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	newShelf(t, service, 1, "Comfort shows")
	newShelf(t, service, 1, "Watch with Anna")
	newShelf(t, service, 2, "Someone else's")

	shelves, err := service.Shelves(ctx, 1)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(shelfNames(shelves), []string{"Comfort shows", "Watch with Anna"}) {
		t.Fatalf("shelves = %v", shelfNames(shelves))
	}
}

func TestShelfNamesAreTrimmedAndLimited(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	shelf := newShelf(t, service, 1, "  Comfort shows  ")
	if shelf.Name != "Comfort shows" {
		t.Fatalf("name = %q, want it trimmed", shelf.Name)
	}
	for _, invalid := range []string{"", "   ", strings.Repeat("a", maxShelfNameLength+1)} {
		if _, err := service.CreateShelf(ctx, 1, invalid); !errors.Is(err, ErrInvalidShelfName) {
			t.Fatalf("create %q: err = %v, want invalid name", invalid, err)
		}
	}
	if _, err := service.RenameShelf(ctx, 1, shelf.ID, ""); !errors.Is(err, ErrInvalidShelfName) {
		t.Fatalf("rename to empty: err = %v", err)
	}
}

func TestRenameChangesOnlyTheProfilesOwnShelf(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	shelf := newShelf(t, service, 1, "Old")
	renamed, err := service.RenameShelf(ctx, 1, shelf.ID, "New")
	if err != nil || renamed.Name != "New" {
		t.Fatalf("renamed = %+v, %v", renamed, err)
	}
	if _, err := service.RenameShelf(ctx, 2, shelf.ID, "Stolen"); !errors.Is(err, ErrShelfNotFound) {
		t.Fatalf("another profile's rename: err = %v, want not found", err)
	}
}

func TestTitlesAreAddedAtTheEndOnce(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	shelf := newShelf(t, service, 1, "Comfort shows")
	for _, id := range []int{10, 20, 10, 30} {
		if err := service.AddToShelf(ctx, 1, shelf.ID, movieRef(id)); err != nil {
			t.Fatal(err)
		}
	}
	loaded, err := service.Shelf(ctx, 1, shelf.ID)
	if err != nil {
		t.Fatal(err)
	}
	want := []library.MediaRef{movieRef(10), movieRef(20), movieRef(30)}
	if !slices.Equal(loaded.Titles, want) {
		t.Fatalf("titles = %v, want %v", loaded.Titles, want)
	}
	if err := service.AddToShelf(ctx, 2, shelf.ID, movieRef(40)); !errors.Is(err, ErrShelfNotFound) {
		t.Fatalf("adding to another profile's shelf: err = %v", err)
	}
}

func TestRemovingATitleKeepsTheOrderOfTheRest(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	shelf := newShelf(t, service, 1, "Comfort shows")
	for _, id := range []int{10, 20, 30} {
		if err := service.AddToShelf(ctx, 1, shelf.ID, movieRef(id)); err != nil {
			t.Fatal(err)
		}
	}
	if err := service.RemoveFromShelf(ctx, 1, shelf.ID, movieRef(20)); err != nil {
		t.Fatal(err)
	}
	if err := service.AddToShelf(ctx, 1, shelf.ID, movieRef(40)); err != nil {
		t.Fatal(err)
	}
	loaded, _ := service.Shelf(ctx, 1, shelf.ID)
	want := []library.MediaRef{movieRef(10), movieRef(30), movieRef(40)}
	if !slices.Equal(loaded.Titles, want) {
		t.Fatalf("titles = %v, want %v", loaded.Titles, want)
	}
}

func TestReorderingTitlesAndShelves(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	first := newShelf(t, service, 1, "First")
	newShelf(t, service, 1, "Second")
	third := newShelf(t, service, 1, "Third")
	for _, id := range []int{10, 20, 30} {
		if err := service.AddToShelf(ctx, 1, first.ID, movieRef(id)); err != nil {
			t.Fatal(err)
		}
	}

	if err := service.ReorderShelves(ctx, 1, []int{third.ID, first.ID}); err != nil {
		t.Fatal(err)
	}
	shelves, _ := service.Shelves(ctx, 1)
	if !slices.Equal(shelfNames(shelves), []string{"Third", "First", "Second"}) {
		t.Fatalf("shelves = %v, want the unlisted one last", shelfNames(shelves))
	}

	if err := service.ReorderShelfTitles(ctx, 1, first.ID, []library.MediaRef{movieRef(30), movieRef(10), movieRef(99)}); err != nil {
		t.Fatal(err)
	}
	loaded, _ := service.Shelf(ctx, 1, first.ID)
	want := []library.MediaRef{movieRef(30), movieRef(10), movieRef(20)}
	if !slices.Equal(loaded.Titles, want) {
		t.Fatalf("titles = %v, want %v (unknown titles ignored)", loaded.Titles, want)
	}
}

func TestDeletingAShelfClosesTheGap(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	first := newShelf(t, service, 1, "First")
	second := newShelf(t, service, 1, "Second")
	if err := service.AddToShelf(ctx, 1, first.ID, movieRef(10)); err != nil {
		t.Fatal(err)
	}
	if err := service.DeleteShelf(ctx, 1, first.ID); err != nil {
		t.Fatal(err)
	}
	created := newShelf(t, service, 1, "Third")
	shelves, _ := service.Shelves(ctx, 1)
	if !slices.Equal(shelfNames(shelves), []string{"Second", "Third"}) {
		t.Fatalf("shelves = %v", shelfNames(shelves))
	}
	if shelves[0].ID != second.ID || shelves[1].ID != created.ID || shelves[1].Position != 1 {
		t.Fatalf("shelves = %+v, want positions 0 and 1", shelves)
	}
	if err := service.DeleteShelf(ctx, 1, first.ID); !errors.Is(err, ErrShelfNotFound) {
		t.Fatalf("deleting twice: err = %v", err)
	}
}

func TestShelfLimits(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	for index := range maxShelvesPerProfile {
		newShelf(t, service, 1, strings.Repeat("s", index+1))
	}
	if _, err := service.CreateShelf(ctx, 1, "one too many"); !errors.Is(err, ErrTooManyShelves) {
		t.Fatalf("err = %v, want too many shelves", err)
	}
}

func TestDeletingAProfileDeletesItsShelves(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	created, err := service.Create(ctx, "Frank", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	shelf := newShelf(t, service, created.ID, "Comfort shows")
	if err := service.AddToShelf(ctx, created.ID, shelf.ID, movieRef(10)); err != nil {
		t.Fatal(err)
	}
	if err := service.Delete(ctx, created.ID); err != nil {
		t.Fatal(err)
	}
	shelves, err := service.Shelves(ctx, created.ID)
	if err != nil || len(shelves) != 0 {
		t.Fatalf("shelves = %v, %v", shelves, err)
	}
	left, _ := service.database.Int(ctx, `SELECT COUNT(*) FROM shelf_title`, nil)
	if left != 0 {
		t.Fatalf("%d shelf titles left behind", left)
	}
}

func TestReorderPutsWantedFirstAndIgnoresUnknown(t *testing.T) {
	got := reorder([]int{1, 2, 3, 4}, []int{3, 3, 9, 1})
	if !slices.Equal(got, []int{3, 1, 2, 4}) {
		t.Fatalf("reorder = %v", got)
	}
}
