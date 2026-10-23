package library

import (
	"context"
	"testing"

	"github.com/neoworks/otter/internal/plugins"
)

func TestDeadLinkIsRemovedAndStaysOut(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	ref := MediaRef{MediaType: MediaTypeEpisode, MediaID: 1301565}
	links := []plugins.Download{{URL: "https://dood.yt/w/gone"}, {URL: "https://voe.sx/e/alive"}}
	if err := store.ReplacePluginStreams(ctx, ref, "kinoking", links); err != nil {
		t.Fatal(err)
	}

	if err := store.ForgetDeadLink(ctx, "https://dood.yt/w/gone"); err != nil {
		t.Fatal(err)
	}
	assertStreamURLs(t, store, ref, "https://voe.sx/e/alive")

	// The site still lists it; the next lookup must not bring it back.
	if err := store.ReplacePluginStreams(ctx, ref, "kinoking", links); err != nil {
		t.Fatal(err)
	}
	assertStreamURLs(t, store, ref, "https://voe.sx/e/alive")
}

func assertStreamURLs(t *testing.T, store *Store, ref MediaRef, want ...string) {
	t.Helper()
	streams, err := store.Streams(context.Background(), ref)
	if err != nil {
		t.Fatal(err)
	}
	var got []string
	for _, stream := range streams {
		got = append(got, stream.URL)
	}
	if len(got) != len(want) || (len(got) > 0 && got[0] != want[0]) {
		t.Fatalf("streams = %v, want %v", got, want)
	}
}
