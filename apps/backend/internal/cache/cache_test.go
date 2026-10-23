package cache

import (
	"context"
	"errors"
	"path/filepath"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/db"
)

func openTestCache(t *testing.T) *Client {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() { database.Close() })
	return New(ctx, database)
}

func TestSetGetExpire(t *testing.T) {
	ctx := context.Background()
	client := openTestCache(t)
	if err := client.Set(ctx, "key", "value", time.Hour); err != nil {
		t.Fatalf("set: %v", err)
	}
	if value, err := client.Get(ctx, "key"); err != nil || value != "value" {
		t.Errorf("get = %q, %v", value, err)
	}
	if err := client.Set(ctx, "short", "value", time.Millisecond); err != nil {
		t.Fatalf("set: %v", err)
	}
	time.Sleep(5 * time.Millisecond)
	if _, err := client.Get(ctx, "short"); !errors.Is(err, ErrMiss) {
		t.Errorf("expired get err = %v, want ErrMiss", err)
	}
	if err := client.Del(ctx, "key"); err != nil {
		t.Fatalf("del: %v", err)
	}
	if _, err := client.Get(ctx, "key"); !errors.Is(err, ErrMiss) {
		t.Errorf("deleted get err = %v, want ErrMiss", err)
	}
}

func TestIncrRestartsAfterExpiry(t *testing.T) {
	ctx := context.Background()
	client := openTestCache(t)
	for want := int64(1); want <= 3; want++ {
		if got, err := client.Incr(ctx, "counter", time.Hour); err != nil || got != want {
			t.Fatalf("incr = %d, %v; want %d", got, err, want)
		}
	}
	if _, err := client.Incr(ctx, "brief", time.Millisecond); err != nil {
		t.Fatalf("incr: %v", err)
	}
	time.Sleep(5 * time.Millisecond)
	if got, _ := client.Incr(ctx, "brief", time.Hour); got != 1 {
		t.Errorf("incr after expiry = %d, want 1", got)
	}
	if ttl, _ := client.TTL(ctx, "counter"); ttl <= 0 || ttl > time.Hour {
		t.Errorf("ttl = %v", ttl)
	}
}

func TestEntriesAndDel(t *testing.T) {
	ctx := context.Background()
	client := openTestCache(t)
	_ = client.Set(ctx, "stream:a", "1", time.Hour)
	_ = client.Set(ctx, "stream:b", "2", 0)
	_ = client.Set(ctx, "other:c", "3", time.Hour)
	_ = client.Set(ctx, "stream:gone", "4", time.Nanosecond)
	time.Sleep(time.Millisecond)

	entries, err := client.Entries(ctx, "stream:")
	if err != nil {
		t.Fatalf("entries: %v", err)
	}
	if len(entries) != 2 || entries["stream:a"] != "1" || entries["stream:b"] != "2" {
		t.Errorf("entries = %v", entries)
	}
	if err := client.Del(ctx, "stream:a", "stream:b"); err != nil {
		t.Fatalf("del: %v", err)
	}
	if entries, _ := client.Entries(ctx, "stream:"); len(entries) != 0 {
		t.Errorf("after del = %v", entries)
	}
	if value, _ := client.Get(ctx, "other:c"); value != "3" {
		t.Error("del removed an unrelated key")
	}
}
