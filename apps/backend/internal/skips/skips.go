// Package skips looks up the parts of an episode viewers skip (intro,
// recap, credits, preview) on TheIntroDB, a crowdsourced database keyed by
// TMDB IDs. Answers are cached in kv.
package skips

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/neoworks/otter/internal/cache"
)

const (
	defaultBaseURL = "https://api.theintrodb.org/v3/media"
	kvPrefix       = "skips:v1:"
	// Segments rarely change once submitted; episodes nobody timed yet may
	// get timed soon after they air.
	ttlFound   = 7 * 24 * time.Hour
	ttlMissing = 24 * time.Hour
)

// Segment kinds as TheIntroDB names them.
const (
	KindIntro   = "intro"
	KindRecap   = "recap"
	KindCredits = "credits"
	KindPreview = "preview"
)

var kinds = []string{KindIntro, KindRecap, KindCredits, KindPreview}

// Segment is one skippable part. EndSeconds is nil when it runs to the end.
type Segment struct {
	Kind         string   `json:"kind"`
	StartSeconds float64  `json:"startSeconds"`
	EndSeconds   *float64 `json:"endSeconds"`
}

type Client struct {
	kv         *cache.Client
	httpClient *http.Client
	baseURL    string
}

func New(kv *cache.Client) *Client {
	return &Client{kv: kv, httpClient: &http.Client{Timeout: 10 * time.Second}, baseURL: defaultBaseURL}
}

// Episode returns the skippable parts of an episode of the series with the
// given TMDB ID; none when nobody submitted any.
func (c *Client) Episode(ctx context.Context, seriesTMDBID, season, episode int) ([]Segment, error) {
	key := fmt.Sprintf("%stv:%d:%d:%d", kvPrefix, seriesTMDBID, season, episode)
	if cached, ok := c.cached(ctx, key); ok {
		return cached, nil
	}
	query := url.Values{}
	query.Set("tmdb_id", fmt.Sprint(seriesTMDBID))
	query.Set("season", fmt.Sprint(season))
	query.Set("episode", fmt.Sprint(episode))
	segments, err := c.fetch(ctx, query)
	if err != nil {
		return nil, err
	}
	c.store(ctx, key, segments)
	return segments, nil
}

func (c *Client) cached(ctx context.Context, key string) ([]Segment, bool) {
	value, err := c.kv.Get(ctx, key)
	if err != nil {
		return nil, false
	}
	var segments []Segment
	if err := json.Unmarshal([]byte(value), &segments); err != nil {
		return nil, false
	}
	return segments, true
}

func (c *Client) store(ctx context.Context, key string, segments []Segment) {
	ttl := ttlFound
	if len(segments) == 0 {
		ttl = ttlMissing
	}
	encoded, err := json.Marshal(segments)
	if err != nil {
		return
	}
	_ = c.kv.Set(ctx, key, string(encoded), ttl)
}

// mediaResponse is TheIntroDB's answer: a list of timed parts per kind.
type mediaResponse map[string]json.RawMessage

type timedPart struct {
	StartMilliseconds *float64 `json:"start_ms"`
	EndMilliseconds   *float64 `json:"end_ms"`
}

func (c *Client) fetch(ctx context.Context, query url.Values) ([]Segment, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"?"+query.Encode(), nil)
	if err != nil {
		return nil, err
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotFound {
		return []Segment{}, nil
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("TheIntroDB: HTTP %d", response.StatusCode)
	}
	var body mediaResponse
	if err := json.NewDecoder(response.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("TheIntroDB: %w", err)
	}
	return segmentsOf(body), nil
}

// segmentsOf reads every kind's parts, dropping empty ones.
func segmentsOf(body mediaResponse) []Segment {
	segments := []Segment{}
	for _, kind := range kinds {
		var parts []timedPart
		if err := json.Unmarshal(body[kind], &parts); err != nil {
			continue
		}
		for _, part := range parts {
			segment, ok := segmentOf(kind, part)
			if ok {
				segments = append(segments, segment)
			}
		}
	}
	return segments
}

// segmentOf converts one part; a missing start means the very beginning,
// a missing end the very end, and a part without either says nothing.
func segmentOf(kind string, part timedPart) (Segment, bool) {
	start := 0.0
	if part.StartMilliseconds != nil {
		start = *part.StartMilliseconds / 1000
	}
	if part.EndMilliseconds == nil {
		return Segment{Kind: kind, StartSeconds: start}, start > 0
	}
	end := *part.EndMilliseconds / 1000
	if end <= start {
		return Segment{}, false
	}
	return Segment{Kind: kind, StartSeconds: start, EndSeconds: &end}, true
}
