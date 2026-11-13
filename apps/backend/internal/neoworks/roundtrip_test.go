package neoworks

import (
	"context"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	libneoworks "github.com/neoworks-dev/libneoworks-go"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/profiles"
)

// localClient is a libneoworks client that was never signed in: it keeps rows in
// its own store, which is enough to see how content comes back from libneoworks.
func localClient(t *testing.T) *libneoworks.Client {
	t.Helper()
	install, err := libneoworks.NewInstall()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(install.Close)
	client, err := libneoworks.OpenClient(install, filepath.Join(t.TempDir(), "store.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(client.Close)
	check, err := client.RegisterDescriptor(collection, descriptor, schemaVersion)
	if err != nil {
		t.Fatal(err)
	}
	if check != nil {
		// The registry serves the published descriptor's bytes.
		if err := client.CompleteDescriptorCheck(collection, 200, descriptor); err != nil {
			t.Fatalf("descriptor check (%s %s): %v", check.Method, check.URL, err)
		}
	}
	if _, err := client.CreateRoot(collection); err != nil {
		t.Fatalf("local root: %v", err)
	}
	return client
}

// device is one Otter install: its database and its libneoworks store.
type device struct {
	database *db.DB
	mirror   profileMirror
	client   *libneoworks.Client
}

func newDevice(t *testing.T) device {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "otter.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	client := localClient(t)
	return device{database: database, client: client, mirror: profileMirror{database: database, store: clientRows{client: client}}}
}

func (d device) addProfile(t *testing.T, name string) string {
	t.Helper()
	_, err := d.database.Exec(context.Background(),
		`INSERT INTO profile (name, created_at, languages, subtitle_size) VALUES ($name, $createdAt, '["de","en"]', 'large')`,
		db.Vars{"name": name, "createdAt": db.Now()})
	if err != nil {
		t.Fatal(err)
	}
	id, err := d.database.Int(context.Background(), `SELECT id FROM profile WHERE name = $name`, db.Vars{"name": name})
	if err != nil {
		t.Fatal(err)
	}
	return strconv.Itoa(id)
}

func (d device) profileNames(t *testing.T) []string {
	t.Helper()
	names, err := d.database.Strings(context.Background(), `SELECT name FROM profile ORDER BY name`, nil)
	if err != nil {
		t.Fatal(err)
	}
	return names
}

func (d device) sync(t *testing.T) (received, sent int) {
	t.Helper()
	received, err := d.mirror.pull(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	sent, err = d.mirror.push(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	return received, sent
}

func profileNamed(name string) profiles.Profile {
	return profiles.Profile{Name: name, Languages: `["de","en"]`, SubtitleSize: profiles.SubtitleSizeMedium, CreatedAt: db.Now()}
}

func timeNow() time.Time {
	return time.Now()
}

func TestLocalProfilesBecomeRowsOnce(t *testing.T) {
	phone := newDevice(t)
	phone.addProfile(t, "Moritz")
	phone.addProfile(t, "Kids")

	if _, sent := phone.sync(t); sent != 2 {
		t.Fatalf("first sync wrote %d rows, want 2", sent)
	}
	// What libneoworks hands back must hash like what was written, or every
	// sync would see a change and write the profiles again.
	if received, sent := phone.sync(t); received != 0 || sent != 0 {
		t.Fatalf("unchanged profiles: received %d, sent %d", received, sent)
	}
}

func TestAProfileChangedHereIsWrittenAgain(t *testing.T) {
	phone := newDevice(t)
	id := phone.addProfile(t, "Moritz")
	phone.sync(t)
	if _, err := phone.database.Exec(context.Background(), `UPDATE profile SET voice_boost = 1 WHERE id = $id`, db.Vars{"id": id}); err != nil {
		t.Fatal(err)
	}
	if _, sent := phone.sync(t); sent != 1 {
		t.Fatalf("changed profile: sent %d, want 1", sent)
	}
	rows, _, err := phone.mirror.store.rows(tableProfile)
	if err != nil {
		t.Fatal(err)
	}
	for _, content := range rows {
		if content["voiceBoost"] != true {
			t.Fatalf("row content %v", content)
		}
	}
}

func TestAProfileDeletedHereDeletesItsRow(t *testing.T) {
	phone := newDevice(t)
	id := phone.addProfile(t, "Moritz")
	phone.sync(t)
	if _, err := phone.database.Exec(context.Background(), `DELETE FROM profile WHERE id = $id`, db.Vars{"id": id}); err != nil {
		t.Fatal(err)
	}
	if _, sent := phone.sync(t); sent != 1 {
		t.Fatalf("deleted profile: sent %d, want 1", sent)
	}
	rows, _, err := phone.mirror.store.rows(tableProfile)
	if err != nil || len(rows) != 0 {
		t.Fatalf("rows left: %v, %v", rows, err)
	}
}

func TestAProfileFromAnotherDeviceAppearsHere(t *testing.T) {
	phone := newDevice(t)
	// Another device wrote this row; here it arrives through a pull.
	content := profileContent(profileNamed("Grandma"), timeNow())
	if _, err := phone.mirror.store.createRow(tableProfile, content); err != nil {
		t.Fatal(err)
	}
	if received, _ := phone.sync(t); received != 1 {
		t.Fatalf("received %d, want the new profile", received)
	}
	if names := phone.profileNames(t); len(names) != 1 || names[0] != "Grandma" {
		t.Fatalf("profiles here: %v", names)
	}
	if received, sent := phone.sync(t); received != 0 || sent != 0 {
		t.Fatalf("after applying: received %d, sent %d", received, sent)
	}
}

func TestAProfileOnBothDevicesBeforeTheFirstSyncStaysOne(t *testing.T) {
	phone := newDevice(t)
	phone.addProfile(t, "Moritz")
	if _, err := phone.mirror.store.createRow(tableProfile, profileContent(profileNamed("Moritz"), timeNow())); err != nil {
		t.Fatal(err)
	}
	phone.sync(t)
	if names := phone.profileNames(t); len(names) != 1 {
		t.Fatalf("profiles here: %v, want one Moritz", names)
	}
	if rows, _, _ := phone.mirror.store.rows(tableProfile); len(rows) != 1 {
		t.Fatalf("%d rows, want the one row the profile was linked to", len(rows))
	}
}

func TestAProfileDeletedElsewhereIsDeletedHereWithItsHistory(t *testing.T) {
	phone := newDevice(t)
	id := phone.addProfile(t, "Moritz")
	phone.sync(t)
	if _, err := phone.database.Exec(context.Background(),
		`INSERT INTO watch_history (profile_id, media_type, media_id, started_at, updated_at) VALUES ($id, 'movie', 603, $now, $now)`,
		db.Vars{"id": id, "now": db.Now()}); err != nil {
		t.Fatal(err)
	}
	rows, _, _ := phone.mirror.store.rows(tableProfile)
	for rowID := range rows {
		if err := phone.mirror.store.deleteRow(rowID); err != nil {
			t.Fatal(err)
		}
	}
	phone.sync(t)
	if names := phone.profileNames(t); len(names) != 0 {
		t.Fatalf("profiles left: %v", names)
	}
	history, _ := phone.database.Int(context.Background(), `SELECT COUNT(*) FROM watch_history`, nil)
	if history != 0 {
		t.Fatalf("%d history entries left", history)
	}
}
