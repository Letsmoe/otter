// Package cache is an expiring key-value store in the SQLite kv table. It
// replaced Redis: the stream cache index and playback proxy sessions live here.
package cache

import (
	"context"
	"errors"
	"fmt"
	"log"
	"time"

	"github.com/neoworks/otter/internal/db"
)

// ErrMiss is returned by Get when the key is absent or expired.
var ErrMiss = errors.New("cache: miss")

// sweepInterval is how often expired keys are deleted in the background.
const sweepInterval = time.Hour

type Client struct {
	database *db.DB
}

// New returns a cache over database and sweeps expired keys until ctx ends.
func New(ctx context.Context, database *db.DB) *Client {
	client := &Client{database: database}
	go client.sweep(ctx)
	return client
}

// Get returns the value for key, or ErrMiss.
func (c *Client) Get(ctx context.Context, key string) (string, error) {
	row, err := c.database.QueryRow(ctx,
		`SELECT value FROM kv WHERE key = $key AND (expires_at IS NULL OR expires_at > $now)`,
		db.Vars{"key": key, "now": time.Now().UnixMilli()})
	if err != nil {
		return "", err
	}
	if row == nil {
		return "", ErrMiss
	}
	value, _ := row["value"].(string)
	return value, nil
}

// Set stores value under key; ttl <= 0 means it never expires.
func (c *Client) Set(ctx context.Context, key string, value any, ttl time.Duration) error {
	_, err := c.database.Exec(ctx,
		`INSERT INTO kv (key, value, expires_at) VALUES ($key, $value, $expires)
		 ON CONFLICT (key) DO UPDATE SET value = excluded.value, expires_at = excluded.expires_at`,
		db.Vars{"key": key, "value": fmt.Sprint(value), "expires": expiresAt(ttl)})
	return err
}

// Entries returns every unexpired key starting with prefix, with its value.
func (c *Client) Entries(ctx context.Context, prefix string) (map[string]string, error) {
	rows, err := c.database.Query(ctx,
		`SELECT key, value FROM kv WHERE substr(key, 1, length($prefix)) = $prefix
		 AND (expires_at IS NULL OR expires_at > $now)`,
		db.Vars{"prefix": prefix, "now": time.Now().UnixMilli()})
	if err != nil {
		return nil, err
	}
	entries := make(map[string]string, len(rows))
	for _, row := range rows {
		key, _ := row["key"].(string)
		value, _ := row["value"].(string)
		entries[key] = value
	}
	return entries, nil
}

// Del removes keys.
func (c *Client) Del(ctx context.Context, keys ...string) error {
	if len(keys) == 0 {
		return nil
	}
	_, err := c.database.Exec(ctx, `DELETE FROM kv WHERE key IN (SELECT value FROM json_each($keys))`,
		db.Vars{"keys": keys})
	return err
}

// Incr increments key by 1 and refreshes its TTL. A missing or expired key
// starts from 0.
func (c *Client) Incr(ctx context.Context, key string, ttl time.Duration) (int64, error) {
	row, err := c.database.QueryRow(ctx,
		`INSERT INTO kv (key, value, expires_at) VALUES ($key, '1', $expires)
		 ON CONFLICT (key) DO UPDATE SET
		   value = CASE WHEN kv.expires_at IS NOT NULL AND kv.expires_at <= $now THEN '1'
		                ELSE CAST(CAST(kv.value AS INTEGER) + 1 AS TEXT) END,
		   expires_at = excluded.expires_at
		 RETURNING value`,
		db.Vars{"key": key, "expires": expiresAt(ttl), "now": time.Now().UnixMilli()})
	if err != nil || row == nil {
		return 0, err
	}
	return int64(db.AsInt(row["value"])), nil
}

// TTL returns the remaining lifetime of key; 0 when absent, expired, or
// without expiry.
func (c *Client) TTL(ctx context.Context, key string) (time.Duration, error) {
	row, err := c.database.QueryRow(ctx, `SELECT expires_at FROM kv WHERE key = $key`, db.Vars{"key": key})
	if err != nil || row == nil || row["expires_at"] == nil {
		return 0, err
	}
	remaining := time.Until(time.UnixMilli(int64(db.AsInt(row["expires_at"]))))
	if remaining < 0 {
		return 0, nil
	}
	return remaining, nil
}

// sweep deletes expired keys every sweepInterval.
func (c *Client) sweep(ctx context.Context) {
	ticker := time.NewTicker(sweepInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if _, err := c.database.Exec(ctx, `DELETE FROM kv WHERE expires_at <= $now`,
				db.Vars{"now": time.Now().UnixMilli()}); err != nil {
				log.Printf("cache sweep: %v", err)
			}
		}
	}
}

// expiresAt converts a TTL into unix milliseconds, or nil for no expiry.
func expiresAt(ttl time.Duration) any {
	if ttl <= 0 {
		return nil
	}
	return time.Now().Add(ttl).UnixMilli()
}
