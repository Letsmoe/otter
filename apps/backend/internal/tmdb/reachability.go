package tmdb

import (
	"context"
	"errors"
	"sync/atomic"
)

// reachability remembers whether the last request got any HTTP answer from
// TMDB. Requests the caller cancelled say nothing about TMDB and are ignored.
type reachability struct {
	unreachable atomic.Bool
}

func (r *reachability) record(requestErr error) {
	if requestErr == nil {
		r.unreachable.Store(false)
		return
	}
	if errors.Is(requestErr, context.Canceled) {
		return
	}
	r.unreachable.Store(true)
}

// Reachable reports whether the last request reached TMDB; before the first
// request it counts as reachable.
func (c *Client) Reachable() bool {
	return !c.reachability.unreachable.Load()
}

// CheckConnection asks TMDB right now and reports whether it answered. A
// rejected or missing key still counts: TMDB itself is reachable.
func (c *Client) CheckConnection(ctx context.Context) bool {
	_, _ = c.CheckKey(ctx, c.apiKey())
	return c.Reachable()
}
