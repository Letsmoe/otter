package downloads

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	return NewStore(database)
}

var matrix = library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 603}

func TestQueueKeepsRunningAndRequeuesFailed(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)

	queued, err := store.Queue(ctx, 1, matrix, 0, 0)
	if err != nil || queued.Status != StatusQueued {
		t.Fatalf("queue: %+v %v", queued, err)
	}
	if err := store.MarkDownloading(ctx, queued.ID); err != nil {
		t.Fatal(err)
	}
	again, _ := store.Queue(ctx, 1, matrix, 0, 0)
	if again.ID != queued.ID || again.Status != StatusDownloading {
		t.Fatalf("running download must stay running: %+v", again)
	}

	if err := store.MarkFailed(ctx, queued.ID, "boom"); err != nil {
		t.Fatal(err)
	}
	retried, _ := store.Queue(ctx, 1, matrix, 0, 0)
	if retried.Status != StatusQueued || retried.Error != "" {
		t.Fatalf("failed download must be re-queued: %+v", retried)
	}
}

func TestDoneDownloadsArePinned(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	queued, _ := store.Queue(ctx, 1, matrix, 0, 0)
	if err := store.MarkDone(ctx, queued.ID, "http://local/blobs/stream-cache/mp4/a.mp4", "video/mp4"); err != nil {
		t.Fatal(err)
	}
	pinned, err := store.PinnedURLs(ctx)
	if err != nil || len(pinned) != 1 {
		t.Fatalf("pinned = %v, %v", pinned, err)
	}
	unfinished, _ := store.Unfinished(ctx)
	if len(unfinished) != 0 {
		t.Fatalf("done download listed as unfinished: %+v", unfinished)
	}
}

func TestRequeueTakesTheNewlyPickedLink(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	queued, _ := store.Queue(ctx, 1, matrix, 0, 7)
	if queued.StreamID != 7 {
		t.Fatalf("picked link not stored: %+v", queued)
	}
	if err := store.MarkFailed(ctx, queued.ID, "dead link"); err != nil {
		t.Fatal(err)
	}
	retried, _ := store.Queue(ctx, 1, matrix, 0, 9)
	if retried.StreamID != 9 || retried.Status != StatusQueued {
		t.Fatalf("retry must use the new link: %+v", retried)
	}
}
