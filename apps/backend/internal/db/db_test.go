package db

import (
	"context"
	"path/filepath"
	"testing"
)

func openTestDB(t *testing.T) *DB {
	t.Helper()
	database, err := Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	return database
}

func TestMigrationsApplyOnce(t *testing.T) {
	ctx := context.Background()
	database := openTestDB(t)
	if err := database.migrate(ctx); err != nil {
		t.Fatalf("second migrate: %v", err)
	}
	count, err := database.Int(ctx, "SELECT COUNT(*) FROM _migration", nil)
	if err != nil {
		t.Fatal(err)
	}
	names, _ := migrationNames()
	if count != len(names) {
		t.Fatalf("recorded %d migrations, have %d files", count, len(names))
	}
}

func TestJSONAndBooleanColumnsDecode(t *testing.T) {
	ctx := context.Background()
	database := openTestDB(t)
	_, err := database.Exec(ctx, `INSERT INTO movie (tmdb_id, title, genres, adult, created_at)
		VALUES ($id, $title, $genres, $adult, $now)`,
		Vars{"id": 603, "title": "The Matrix", "genres": []string{"Action", "Science Fiction"}, "adult": false, "now": Now()})
	if err != nil {
		t.Fatal(err)
	}
	type movieRow struct {
		Title  string   `json:"title"`
		Genres []string `json:"genres"`
		Adult  bool     `json:"adult"`
	}
	movie, err := QueryOneAs[movieRow](ctx, database, "SELECT title, genres, adult FROM movie WHERE tmdb_id = $id", Vars{"id": 603})
	if err != nil {
		t.Fatal(err)
	}
	if movie == nil || len(movie.Genres) != 2 || movie.Genres[1] != "Science Fiction" || movie.Adult {
		t.Fatalf("decoded %+v", movie)
	}
}
