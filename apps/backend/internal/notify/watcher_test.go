package notify

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

func TestFollowedTitlesHonourTheBell(t *testing.T) {
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer database.Close()
	statements := []string{
		// 603 on My List, 604 on My List but muted, 605 followed with the bell only.
		`INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES (1, 'movie', 603, 'x'), (1, 'movie', 604, 'x')`,
		`INSERT INTO notification_follow (profile_id, media_type, media_id, following, followed_at)
			VALUES (1, 'movie', 604, 0, 'x'), (1, 'movie', 605, 1, 'x')`,
	}
	for _, statement := range statements {
		if _, err := database.Exec(ctx, statement, nil); err != nil {
			t.Fatal(err)
		}
	}
	watcher := NewWatcher(NewStore(database), database, nil)
	followed, err := watcher.followedTitles(ctx)
	if err != nil {
		t.Fatal(err)
	}
	for mediaID, want := range map[int]bool{603: true, 604: false, 605: true} {
		_, got := followed[library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: mediaID}]
		if got != want {
			t.Fatalf("movie %d followed = %v, want %v", mediaID, got, want)
		}
	}
}
