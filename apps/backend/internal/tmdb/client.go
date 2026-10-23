// Package tmdb is a thin TMDB v3 API client. It only fetches and decodes;
// persisting the results is the library package's job.
package tmdb

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"
)

const (
	apiBase   = "https://api.themoviedb.org/3"
	imageBase = "https://image.tmdb.org/t/p/original"
)

// ConfigSource returns the current API key and response language.
type ConfigSource func() (apiKey string, language string)

type Client struct {
	config     ConfigSource
	httpClient *http.Client
	baseURL    string
	genres     genreCache
	rejected   rejectedKeys
}

func NewClient(config ConfigSource) *Client {
	return &Client{
		config:     config,
		httpClient: &http.Client{Timeout: 15 * time.Second},
		baseURL:    apiBase,
	}
}

// WithBaseURL points the client at another API root (tests use a fake TMDB).
func (c *Client) WithBaseURL(baseURL string) *Client {
	c.baseURL = baseURL
	return c
}

func (c *Client) apiKey() string {
	apiKey, _ := c.config()
	return apiKey
}

func (c *Client) language() string {
	_, language := c.config()
	if language == "" {
		return "en-US"
	}
	return language
}

func (c *Client) Configured() bool {
	return c.apiKey() != ""
}

// get performs a TMDB v3 GET request and decodes the JSON response into out.
func (c *Client) get(ctx context.Context, path string, params map[string]string, out any) error {
	key := c.apiKey()
	if key == "" {
		return fmt.Errorf("tmdb: api key not configured")
	}

	query := url.Values{}
	query.Set("api_key", key)
	query.Set("language", c.language())
	for name, value := range params {
		query.Set(name, value)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path+"?"+query.Encode(), nil)
	if err != nil {
		return err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("tmdb %s: %w", path, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusUnauthorized {
		c.rejected.record(key, true)
		return fmt.Errorf("tmdb %s: %w", path, ErrInvalidKey)
	}
	c.rejected.record(key, false)
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("tmdb %s: HTTP %d", path, resp.StatusCode)
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

// ImageURL returns the full CDN URL for a TMDB image path ("" stays "").
func ImageURL(path string) string {
	if path == "" {
		return ""
	}
	return imageBase + path
}
