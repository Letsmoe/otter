package tmdb

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sync"
)

// KeyStatus says whether TMDB accepts the configured API key.
type KeyStatus string

const (
	KeyStatusOK      KeyStatus = "ok"
	KeyStatusMissing KeyStatus = "missing"
	KeyStatusInvalid KeyStatus = "invalid"
)

// ErrInvalidKey is returned when TMDB rejects the API key (HTTP 401).
var ErrInvalidKey = errors.New("tmdb: api key rejected")

// rejectedKeys remembers keys TMDB answered 401 for, so the app can ask for a
// new one. A key counts as working again once a request with it succeeds.
type rejectedKeys struct {
	mutex sync.Mutex
	keys  map[string]bool
}

func (r *rejectedKeys) record(key string, rejected bool) {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	if r.keys == nil {
		r.keys = map[string]bool{}
	}
	if rejected {
		r.keys[key] = true
		return
	}
	delete(r.keys, key)
}

func (r *rejectedKeys) contains(key string) bool {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	return r.keys[key]
}

// KeyStatus reports the configured key's status from the last request made
// with it; a key not tried yet counts as ok.
func (c *Client) KeyStatus() KeyStatus {
	key := c.apiKey()
	if key == "" {
		return KeyStatusMissing
	}
	if c.rejected.contains(key) {
		return KeyStatusInvalid
	}
	return KeyStatusOK
}

// VerifyKey asks TMDB whether the configured key works, updating KeyStatus.
func (c *Client) VerifyKey(ctx context.Context) {
	key := c.apiKey()
	if key == "" {
		return
	}
	valid, err := c.CheckKey(ctx, key)
	if err != nil {
		return
	}
	c.rejected.record(key, !valid)
}

// CheckKey asks TMDB whether key is a valid API key. An error means TMDB
// could not be asked (offline), not that the key is wrong.
func (c *Client) CheckKey(ctx context.Context, key string) (bool, error) {
	query := url.Values{}
	query.Set("api_key", key)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/configuration?"+query.Encode(), nil)
	if err != nil {
		return false, err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return false, fmt.Errorf("tmdb: check key: %w", err)
	}
	resp.Body.Close()
	if resp.StatusCode == http.StatusUnauthorized {
		return false, nil
	}
	if resp.StatusCode != http.StatusOK {
		return false, fmt.Errorf("tmdb: check key: HTTP %d", resp.StatusCode)
	}
	return true, nil
}
