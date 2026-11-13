package neoworks

import (
	"context"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/db"
)

// syncAll runs one full mirror pass on a device, like a sync does between its exchanges.
func (d device) syncAll(t *testing.T) SyncResult {
	t.Helper()
	result, err := (&Service{database: d.database}).mirrorAll(context.Background(), d.mirror.store)
	if err != nil {
		t.Fatal(err)
	}
	return result
}

func (d device) exec(t *testing.T, statement string, values db.Vars) {
	t.Helper()
	if _, err := d.database.Exec(context.Background(), statement, values); err != nil {
		t.Fatal(err)
	}
}

func (d device) count(t *testing.T, query string) int {
	t.Helper()
	count, err := d.database.Int(context.Background(), query, nil)
	if err != nil {
		t.Fatal(err)
	}
	return count
}

func at(minutesAgo int) string {
	return db.FormatTime(time.Now().Add(-time.Duration(minutesAgo) * time.Minute))
}

func (d device) watch(t *testing.T, profileID string, mediaID int, progress float64, completed bool, updatedAt string) {
	t.Helper()
	d.exec(t, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds, duration_seconds, completed, started_at, updated_at)
		VALUES ($profile, 'movie', $media, $progress, 7200.5, $completed, $updatedAt, $updatedAt)
		ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET progress_seconds = excluded.progress_seconds,
		  completed = excluded.completed, updated_at = excluded.updated_at`,
		db.Vars{"profile": profileID, "media": mediaID, "progress": progress, "completed": completed, "updatedAt": updatedAt})
}

func (d device) progressOf(t *testing.T, mediaID int) (float64, bool) {
	t.Helper()
	row, err := d.database.QueryRow(context.Background(), `SELECT progress_seconds, completed FROM watch_history WHERE media_id = $media`, db.Vars{"media": mediaID})
	if err != nil || row == nil {
		t.Fatalf("no progress for %d: %v", mediaID, err)
	}
	return numberOf(row["progress_seconds"]), truthOf(row["completed"])
}

// progressRowContent edits the one WatchProgress row as another device would.
func (d device) editProgressRow(t *testing.T, changes map[string]any) {
	t.Helper()
	rows, _, err := d.mirror.store.rows(tableWatchProgress)
	if err != nil || len(rows) != 1 {
		t.Fatalf("%d progress rows, %v", len(rows), err)
	}
	for rowID := range rows {
		if err := d.mirror.store.updateRow(rowID, changes); err != nil {
			t.Fatal(err)
		}
	}
}

func TestHistoryListAndRatingsSyncOnceWithTheirProfile(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.watch(t, profileID, 603, 1234.5, false, at(5))
	phone.exec(t, `INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES ($p, 'series', 1396, $now)`, db.Vars{"p": profileID, "now": db.Now()})
	phone.exec(t, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at) VALUES ($p, 'movie', 603, 7.5, $now)`, db.Vars{"p": profileID, "now": db.Now()})

	if result := phone.syncAll(t); result.Sent != 4 {
		t.Fatalf("first sync sent %d rows, want the profile and its three records", result.Sent)
	}
	// Seconds travel as nanosecond strings; reading them back must not look like a change.
	if result := phone.syncAll(t); result.Sent != 0 || result.Received != 0 {
		t.Fatalf("unchanged records: %+v", result)
	}
}

func TestNewerProgressFromAnotherDeviceIsApplied(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.watch(t, profileID, 603, 600, false, at(30))
	phone.syncAll(t)

	phone.editProgressRow(t, map[string]any{"position": nanosecondsText(3000), "updatedAt": at(1)})
	if result := phone.syncAll(t); result.Received != 1 {
		t.Fatalf("received %d, want the newer progress", result.Received)
	}
	if progress, _ := phone.progressOf(t, 603); progress != 3000 {
		t.Fatalf("progress %v, want 3000", progress)
	}
}

func TestOlderProgressFromAnotherDeviceLosesToTheNewerOneHere(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.watch(t, profileID, 603, 600, false, at(30))
	phone.syncAll(t)

	phone.watch(t, profileID, 603, 4000, false, at(1))
	phone.editProgressRow(t, map[string]any{"position": nanosecondsText(100), "updatedAt": at(10)})
	result := phone.syncAll(t)
	if progress, _ := phone.progressOf(t, 603); progress != 4000 {
		t.Fatalf("progress %v, want the newer 4000 kept", progress)
	}
	if result.Sent != 1 {
		t.Fatalf("sent %d, want the newer progress written over the row", result.Sent)
	}
}

func TestAFinishedTitleStaysFinished(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.watch(t, profileID, 603, 7100, true, at(30))
	phone.syncAll(t)

	phone.editProgressRow(t, map[string]any{"position": nanosecondsText(50), "completed": false, "updatedAt": at(1)})
	phone.syncAll(t)
	if _, completed := phone.progressOf(t, 603); !completed {
		t.Fatal("another device's restart undid finished")
	}
}

func TestRemovingFromMyListRemovesItEverywhere(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.exec(t, `INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES ($p, 'movie', 603, $now)`, db.Vars{"p": profileID, "now": db.Now()})
	phone.syncAll(t)

	phone.exec(t, `DELETE FROM watchlist`, nil)
	if result := phone.syncAll(t); result.Sent != 1 {
		t.Fatalf("sent %d, want the entry's row deleted", result.Sent)
	}
	if rows, _, _ := phone.mirror.store.rows(tableWatchlistEntry); len(rows) != 0 {
		t.Fatalf("%d list rows left", len(rows))
	}
}

func TestAnEntryRemovedElsewhereLeavesMyListHere(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.exec(t, `INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES ($p, 'movie', 603, $now)`, db.Vars{"p": profileID, "now": db.Now()})
	phone.syncAll(t)

	rows, _, _ := phone.mirror.store.rows(tableWatchlistEntry)
	for rowID := range rows {
		if err := phone.mirror.store.deleteRow(rowID); err != nil {
			t.Fatal(err)
		}
	}
	phone.syncAll(t)
	if left := phone.count(t, `SELECT COUNT(*) FROM watchlist`); left != 0 {
		t.Fatalf("%d entries left on My List", left)
	}
}

func TestTwoRowsForOneTitleBecomeOne(t *testing.T) {
	phone := newDevice(t)
	profileID := phone.addProfile(t, "Moritz")
	phone.watch(t, profileID, 603, 600, false, at(30))
	phone.syncAll(t)

	// Another device started the same movie before it synced: a second row.
	rows, _, _ := phone.mirror.store.rows(tableProfile)
	var profileRowID string
	for rowID := range rows {
		profileRowID = rowID
	}
	second := map[string]any{
		"profile": profileRowID, "mediaType": "movie", "tmdbId": "603", "position": nanosecondsText(2000),
		"runtime": nanosecondsText(7200), "completed": false, "startedAt": at(20), "updatedAt": at(2),
	}
	if _, err := phone.mirror.store.createRow(tableWatchProgress, second); err != nil {
		t.Fatal(err)
	}
	phone.syncAll(t)
	if progress, _ := phone.progressOf(t, 603); progress != 2000 {
		t.Fatalf("progress %v, want the newer row's 2000", progress)
	}
	if rows, _, _ := phone.mirror.store.rows(tableWatchProgress); len(rows) != 1 {
		t.Fatalf("%d progress rows, want one", len(rows))
	}
}
