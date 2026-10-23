package stream

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

const (
	sessPrefix = "stream:sess:v1:"
	sessTTL    = 4 * time.Hour
)

type sessionData struct {
	Headers map[string]string `json:"headers"`
}

// Proxy is an HTTP handler that serves HLS streams through the backend.
// On first request for a segment it fetches from the CDN and writes to
// the blob store in parallel (write-through cache); subsequent requests redirect
// straight to the blob store. The player always talks to local infrastructure.
//
// Routes (all under a common prefix, e.g. /api/stream/):
//
//	GET /api/stream/manifest?s=<session>&u=<encodedURL>
//	GET /api/stream/seg?s=<session>&h=<hash>&u=<encodedURL>
//	GET /api/stream/mp4?s=<session>&u=<encodedURL>&c=<cacheID>[&direct=1]
//	GET /api/stream/continuous?s=<session>&u=<encodedURL>&start=<seconds> (continuous.go)
type Proxy struct {
	cache   *CacheService
	kv      *cache.Client
	baseURL string // public backend URL, e.g. "http://localhost:8080"
}

func NewProxy(cs *CacheService, kv *cache.Client, baseURL string) *Proxy {
	return &Proxy{cache: cs, kv: kv, baseURL: baseURL}
}

// ManifestURL returns a local proxy URL for the given resolved stream of the
// stored link sourceURL.
// For HLS: stores headers in a kv session and returns the proxy manifest URL.
// For MP4: returns the blob store URL if cached, otherwise returns a proxy URL that
// forwards Range requests to the CDN while background-caching the file.
func (p *Proxy) ManifestURL(ctx context.Context, sourceURL string, result *plugins.ResolveResult) (string, error) {
	// A cache hit is already a local blob. Proxying it would fetch it from
	// ourselves and cache a second copy.
	if strings.HasPrefix(result.URL, p.cache.storage.DirectURL(blobPrefix)) {
		return result.URL, nil
	}
	if !isHLS(result) {
		id, err := p.newSession(ctx, result.Headers)
		if err != nil {
			return "", err
		}
		p.cache.StartCaching(sourceURL, result, result.Headers)
		return p.baseURL + "/api/stream/mp4?s=" + id + "&u=" + encURL(result.URL) + "&c=" + cacheID(sourceURL), nil
	}

	// No fallback to the remote URL: the player must never reach the CDN
	// itself, it may sit outside the backend's VPN (e.g. a Chromecast).
	id, err := p.newSession(ctx, result.Headers)
	if err != nil {
		return "", err
	}
	return p.baseURL + "/api/stream/manifest?s=" + id + "&u=" + encURL(result.URL), nil
}

// ServeHTTP handles /api/stream/manifest, /api/stream/seg, and /api/stream/mp4.
func (p *Proxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	// Any playback request marks the stream as active, so the download queue can
	// hold off while the user is watching (when that setting is enabled).
	if p.cache != nil {
		p.cache.MarkStreamActivity(r.Context())
	}
	switch {
	case strings.HasSuffix(r.URL.Path, "/manifest"):
		p.serveManifest(w, r)
	case strings.HasSuffix(r.URL.Path, "/seg"):
		p.serveSeg(w, r)
	case strings.HasSuffix(r.URL.Path, "/mp4"):
		p.serveMP4(w, r)
	case strings.HasSuffix(r.URL.Path, "/continuous"):
		p.serveContinuous(w, r)
	default:
		http.NotFound(w, r)
	}
}

// serveManifest fetches the upstream m3u8, rewrites all URIs to proxy URLs, and
// serves the rewritten manifest. Always fetches fresh (supports live streams).
func (p *Proxy) serveManifest(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	sessID := q.Get("s")
	manifestURL, err := decURL(q.Get("u"))
	if err != nil {
		http.Error(w, "bad url", http.StatusBadRequest)
		return
	}

	headers, _ := p.loadSession(r.Context(), sessID)

	// Downloaded titles are manifests in our own blob store.
	body, err := p.readPlaylist(r.Context(), manifestURL, headers)
	if err != nil {
		http.Error(w, "upstream: "+err.Error(), http.StatusBadGateway)
		return
	}

	base, _ := url.Parse(manifestURL)
	rewritten := p.rewriteManifest(body, base, sessID)

	w.Header().Set("Content-Type", "application/x-mpegURL")
	w.Header().Set("Cache-Control", "no-cache")
	_, _ = w.Write([]byte(rewritten))
}

// rewriteManifest replaces every URI in an m3u8 with a proxy URL, so the
// player never contacts the CDN itself. They are root-relative, so the player
// resolves them against the host it fetched the manifest from.
func (p *Proxy) rewriteManifest(manifest string, base *url.URL, sessID string) string {
	lines := strings.Split(manifest, "\n")
	out := make([]string, 0, len(lines))
	// The URI after #EXT-X-STREAM-INF is a variant; every other URI line is a
	// segment, whatever tags (BYTERANGE, DISCONTINUITY, …) sit before it.
	nextIsVariant := false

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			if strings.HasPrefix(line, "#EXT-X-STREAM-INF") {
				nextIsVariant = true
			}
			out = append(out, p.rewriteTagURI(line, base, sessID))
			continue
		}
		abs := absURL(base, line)
		if nextIsVariant {
			nextIsVariant = false
			out = append(out, manifestProxyPath(sessID, abs))
			continue
		}
		out = append(out, segmentProxyPath(sessID, abs))
	}
	return strings.Join(out, "\n")
}

func manifestProxyPath(sessID, absoluteURL string) string {
	return "/api/stream/manifest?s=" + sessID + "&u=" + encURL(absoluteURL)
}

func segmentProxyPath(sessID, absoluteURL string) string {
	return "/api/stream/seg?s=" + sessID + "&h=" + urlHash(absoluteURL) + "&u=" + encURL(absoluteURL)
}

// uriAttribute matches the URI="…" attribute of an HLS tag.
var uriAttribute = regexp.MustCompile(`URI="([^"]*)"`)

// playlistTags reference other playlists; every other tag's URI is a file
// (init segment, key, partial segment, preload hint).
var playlistTags = []string{"#EXT-X-MEDIA", "#EXT-X-I-FRAME-STREAM-INF", "#EXT-X-RENDITION-REPORT"}

// rewriteTagURI proxies the URI attribute of any tag that has one: playlists
// through /manifest, files through /seg. Lines without one pass unchanged.
func (p *Proxy) rewriteTagURI(line string, base *url.URL, sessID string) string {
	toPlaylist := isPlaylistTag(line)
	return uriAttribute.ReplaceAllStringFunc(line, func(attribute string) string {
		abs := absURL(base, uriAttribute.FindStringSubmatch(attribute)[1])
		if toPlaylist {
			return `URI="` + manifestProxyPath(sessID, abs) + `"`
		}
		return `URI="` + segmentProxyPath(sessID, abs) + `"`
	})
}

func isPlaylistTag(line string) bool {
	for _, tag := range playlistTags {
		if strings.HasPrefix(line, tag) {
			return true
		}
	}
	return false
}

// serveSeg serves an HLS segment from the blob store (redirect) if cached, otherwise
// fetches from the CDN and tee-writes to the blob store while streaming to the client.
func (p *Proxy) serveSeg(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	sessID := q.Get("s")
	h := q.Get("h")
	segURL, err := decURL(q.Get("u"))
	if err != nil {
		http.Error(w, "bad url", http.StatusBadRequest)
		return
	}

	// A downloaded title's segment: serve the blob on the host the client used.
	if localKey, ok := p.localBlobKey(segURL); ok {
		http.Redirect(w, r, storage.RelativeURL(localKey), http.StatusFound)
		return
	}

	key := blobPrefix + "seg/" + h + segExt(segURL)

	// Cache hit: redirect to the blob store — the player fetches directly, saving backend bandwidth.
	// The touch also confirms the blob still exists; an evicted one is fetched again.
	if v, _ := p.cache.kv.Get(r.Context(), kvSegmentPrefix+h); v == "1" && p.cache.storage.Touch(r.Context(), key) == nil {
		http.Redirect(w, r, storage.RelativeURL(key), http.StatusFound)
		return
	}

	// Cache miss: fetch from CDN and write-through to the blob store.
	headers, _ := p.loadSession(r.Context(), sessID)
	resp, err := p.cache.fetch(r.Context(), segURL, headers)
	if err != nil {
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	ct := resp.Header.Get("Content-Type")
	if ct == "" {
		ct = "video/MP2T"
	}
	w.Header().Set("Content-Type", ct)
	if resp.ContentLength > 0 {
		w.Header().Set("Content-Length", fmt.Sprintf("%d", resp.ContentLength))
	}

	// Pipe body to: (a) HTTP response, (b) the blob store via a goroutine.
	pr, pw := io.Pipe()
	go func() {
		defer pr.Close()
		if err := p.cache.storage.Put(context.Background(), key, pr, resp.ContentLength, ct); err == nil {
			_ = p.cache.kv.Set(context.Background(), kvSegmentPrefix+h, "1", ttlSegment)
		}
	}()

	if _, err := io.Copy(w, io.TeeReader(resp.Body, pw)); err != nil {
		_ = pw.CloseWithError(err)
		return
	}
	pw.Close()
}

// serveMP4 serves an MP4: from the blob store once cached, from the playback
// cache download while it runs, otherwise relayed from the CDN (including any
// Range header). Background caching is started by ManifestURL.
func (p *Proxy) serveMP4(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	mediaURL, err := decURL(q.Get("u"))
	if err != nil {
		http.Error(w, "bad url", http.StatusBadRequest)
		return
	}
	// URLs from before cache IDs named the entry after the resolved URL.
	id := q.Get("c")
	if id == "" {
		id = urlHash(mediaURL)
	}

	// Cache hit: redirect directly to the blob store, on the host the client
	// used. DLNA TVs often don't follow redirects; they ask for direct=1.
	if cached, ok := p.cache.lookupID(r.Context(), id); ok {
		if q.Get("direct") == "1" {
			p.serveBlobDirectly(w, r, cached.URL)
			return
		}
		http.Redirect(w, r, relativeBlobURL(cached.URL), http.StatusFound)
		return
	}

	headers, _ := p.loadSession(r.Context(), q.Get("s"))
	upstream := mp4Upstream{url: mediaURL, headers: headers}
	if file, ok := p.cache.progressiveFile(id); ok && p.serveProgressive(w, r, file, upstream) {
		return
	}
	p.relayMP4(w, r, upstream)
}

// relayMP4 forwards the request to the CDN and streams its answer back.
func (p *Proxy) relayMP4(w http.ResponseWriter, r *http.Request, upstream mp4Upstream) {
	if err := p.cache.checkUpstream(); err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, upstream.url, nil)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
	for k, v := range upstream.headers {
		req.Header.Set(k, v)
	}
	if rng := r.Header.Get("Range"); rng != "" {
		req.Header.Set("Range", rng)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	// Forward relevant response headers.
	for _, h := range []string{"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"} {
		if v := resp.Header.Get(h); v != "" {
			w.Header().Set(h, v)
		}
	}
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

func (p *Proxy) serveBlobDirectly(w http.ResponseWriter, r *http.Request, blobURL string) {
	blobRequest := r.Clone(r.Context())
	blobRequest.URL.Path = relativeBlobURL(blobURL)
	p.cache.storage.Handler().ServeHTTP(w, blobRequest)
}

// localBlobKey reports whether rawURL points at this backend's blob store.
func (p *Proxy) localBlobKey(rawURL string) (string, bool) {
	if p.cache == nil || !strings.HasPrefix(rawURL, p.cache.storage.DirectURL("")) {
		return "", false
	}
	return storage.KeyFromURL(rawURL)
}

func relativeBlobURL(blobURL string) string {
	key, ok := storage.KeyFromURL(blobURL)
	if !ok {
		return blobURL
	}
	return storage.RelativeURL(key)
}

// -- session --

func (p *Proxy) newSession(ctx context.Context, headers map[string]string) (string, error) {
	id := strings.ReplaceAll(uuid.New().String(), "-", "")[:12]
	data, _ := json.Marshal(sessionData{Headers: headers})
	if err := p.kv.Set(ctx, sessPrefix+id, string(data), sessTTL); err != nil {
		return "", err
	}
	return id, nil
}

func (p *Proxy) loadSession(ctx context.Context, id string) (map[string]string, error) {
	val, err := p.kv.Get(ctx, sessPrefix+id)
	if err != nil {
		return nil, err
	}
	var s sessionData
	if err := json.Unmarshal([]byte(val), &s); err != nil {
		return nil, err
	}
	return s.Headers, nil
}

// -- URL encoding --

func encURL(raw string) string {
	return base64.RawURLEncoding.EncodeToString([]byte(raw))
}

func decURL(encoded string) (string, error) {
	b, err := base64.RawURLEncoding.DecodeString(encoded)
	return string(b), err
}
