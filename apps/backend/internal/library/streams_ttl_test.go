package library

import (
	"context"
	"testing"

	"github.com/neoworks/otter/internal/plugins"
)

func TestEmptyLookupsExpireSoonFoundLinksStay(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	service := &Service{Store: store}
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	if err := store.SaveMovie(ctx, Title{MediaType: MediaTypeMovie, TMDBID: 603, Title: "The Matrix"}); err != nil {
		t.Fatal(err)
	}

	ttl, err := service.streamsTTLFor(ctx, ref)
	if err != nil || ttl != emptyStreamsTTL {
		t.Fatalf("ttl %v, err %v; want %v for a title without links", ttl, err, emptyStreamsTTL)
	}

	links := []plugins.Download{{Label: "VOE", URL: "https://voe.sx/a"}}
	if err := store.ReplacePluginStreams(ctx, ref, "kinoger", links); err != nil {
		t.Fatal(err)
	}
	ttl, err = service.streamsTTLFor(ctx, ref)
	if err != nil || ttl != streamsTTL {
		t.Fatalf("ttl %v, err %v; want %v once links were found", ttl, err, streamsTTL)
	}
}

func TestExpireAllStreamsMakesEveryLookupDue(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	if err := store.SaveMovie(ctx, Title{MediaType: MediaTypeMovie, TMDBID: 603, Title: "The Matrix"}); err != nil {
		t.Fatal(err)
	}
	if err := store.MarkStreamsFetched(ctx, ref); err != nil {
		t.Fatal(err)
	}
	fetchedAt, _ := store.StreamsFetchedAt(ctx, ref)
	if isStale(fetchedAt, streamsTTL) {
		t.Fatal("a fresh lookup counts as stale")
	}

	if err := store.ExpireAllStreams(ctx); err != nil {
		t.Fatal(err)
	}
	fetchedAt, err := store.StreamsFetchedAt(ctx, ref)
	if err != nil || !isStale(fetchedAt, streamsTTL) {
		t.Fatalf("fetchedAt %q, err %v; want a due lookup", fetchedAt, err)
	}
}

func TestAnnounceStreamsSaysWhetherALookupIsStillRunning(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	store := newTestStore(t)
	service := &Service{Store: store, StreamUpdates: NewBroker(), lookups: map[MediaRef]bool{}}
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	updates := service.StreamUpdates.Subscribe(ctx, ref)

	service.AnnounceStreams(ctx, ref)
	if update := <-updates; !update.Done {
		t.Fatal("no lookup is running, the update must say done")
	}

	service.setLookingUp(ref, true)
	service.AnnounceStreams(ctx, ref)
	if update := <-updates; update.Done {
		t.Fatal("a lookup is running, the list isn't final")
	}
}
