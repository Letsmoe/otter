// Package db is the SQLite storage layer: connection setup, embedded schema
// migrations, and small query helpers.
//
// Conventions:
//   - Columns declared JSON hold arrays/objects and are decoded on read;
//     columns declared BOOLEAN are decoded to bool.
//   - Timestamps are TEXT in TimeFormat (UTC, fixed width, so they sort).
//   - Queries bind named parameters as $name from a Vars map.
package db

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// TimeFormat is the stored timestamp format. It matches SQLite's
// strftime('%Y-%m-%dT%H:%M:%fZ') so Go- and SQL-written values compare.
const TimeFormat = "2006-01-02T15:04:05.000Z"

// executor is satisfied by both *sql.DB and *sql.Tx.
type executor interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
	QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error)
}

// DB wraps a SQLite connection pool, or a transaction on one (see Tx).
type DB struct {
	pool     *sql.DB
	executor executor
}

// Open opens (creating if needed) the database at path and applies pending
// migrations.
func Open(ctx context.Context, path string) (*DB, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, fmt.Errorf("create database dir: %w", err)
	}
	dsn := "file:" + path +
		"?_pragma=journal_mode(WAL)" +
		"&_pragma=busy_timeout(10000)" +
		"&_pragma=synchronous(NORMAL)" +
		"&_pragma=foreign_keys(0)" +
		"&_txlock=immediate"
	pool, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	pool.SetMaxOpenConns(8)
	if err := pool.PingContext(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping sqlite: %w", err)
	}
	database := &DB{pool: pool, executor: pool}
	if err := database.migrate(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return database, nil
}

// Close closes the connection pool.
func (d *DB) Close() error {
	return d.pool.Close()
}

// Tx runs fn inside a write transaction, committing when fn returns nil.
func (d *DB) Tx(ctx context.Context, fn func(tx *DB) error) error {
	transaction, err := d.pool.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin: %w", err)
	}
	if err := fn(&DB{pool: d.pool, executor: transaction}); err != nil {
		transaction.Rollback()
		return err
	}
	return transaction.Commit()
}

// Now returns the current time in TimeFormat.
func Now() string {
	return FormatTime(time.Now())
}

// FormatTime renders t in TimeFormat.
func FormatTime(t time.Time) string {
	return t.UTC().Format(TimeFormat)
}
