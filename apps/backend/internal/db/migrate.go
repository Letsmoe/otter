package db

import (
	"context"
	"embed"
	"fmt"
	"log"
	"sort"
	"strconv"
	"strings"
)

// Migrations are append-only: never edit an applied file, add a new
// NNN_name.sql instead.
//
//go:embed migrations/*.sql
var migrationFiles embed.FS

// migrate applies every embedded migration not yet recorded in _migration,
// each in its own transaction.
func (d *DB) migrate(ctx context.Context) error {
	if _, err := d.pool.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS _migration (
		version    INTEGER PRIMARY KEY,
		name       TEXT NOT NULL,
		applied_at TEXT NOT NULL
	)`); err != nil {
		return fmt.Errorf("create _migration: %w", err)
	}
	applied, err := d.appliedMigrations(ctx)
	if err != nil {
		return err
	}
	names, err := migrationNames()
	if err != nil {
		return err
	}
	for _, name := range names {
		version, err := migrationVersion(name)
		if err != nil {
			return err
		}
		if applied[version] {
			continue
		}
		if err := d.applyMigration(ctx, version, name); err != nil {
			return err
		}
		log.Printf("db: applied migration %s", name)
	}
	return nil
}

// appliedMigrations returns the set of recorded migration versions.
func (d *DB) appliedMigrations(ctx context.Context) (map[int]bool, error) {
	rows, err := d.pool.QueryContext(ctx, "SELECT version FROM _migration")
	if err != nil {
		return nil, fmt.Errorf("read _migration: %w", err)
	}
	defer rows.Close()
	applied := map[int]bool{}
	for rows.Next() {
		var version int
		if err := rows.Scan(&version); err != nil {
			return nil, err
		}
		applied[version] = true
	}
	return applied, rows.Err()
}

// applyMigration runs one migration file and records it, atomically.
func (d *DB) applyMigration(ctx context.Context, version int, name string) error {
	script, err := migrationFiles.ReadFile("migrations/" + name)
	if err != nil {
		return err
	}
	return d.Tx(ctx, func(tx *DB) error {
		if _, err := tx.executor.ExecContext(ctx, string(script)); err != nil {
			return fmt.Errorf("migration %s: %w", name, err)
		}
		_, err := tx.executor.ExecContext(ctx,
			"INSERT INTO _migration (version, name, applied_at) VALUES (?, ?, ?)", version, name, Now())
		return err
	})
}

// migrationNames lists the embedded migration files in version order.
func migrationNames() ([]string, error) {
	entries, err := migrationFiles.ReadDir("migrations")
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(entries))
	for _, entry := range entries {
		names = append(names, entry.Name())
	}
	sort.Strings(names)
	return names, nil
}

// migrationVersion parses the NNN prefix of "NNN_name.sql".
func migrationVersion(name string) (int, error) {
	prefix, _, found := strings.Cut(name, "_")
	if !found {
		return 0, fmt.Errorf("migration %q: expected NNN_name.sql", name)
	}
	version, err := strconv.Atoi(prefix)
	if err != nil {
		return 0, fmt.Errorf("migration %q: %w", name, err)
	}
	return version, nil
}
