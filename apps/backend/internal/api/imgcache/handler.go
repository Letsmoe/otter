// Package imgcache proxies and caches remote images through the blob store.
//
// Adult-source CDNs (e.g. hqporner) hotlink-protect their images, so the browser
// cannot load them directly. This handler fetches each image server-side with
// browser-like headers, stores it in the blob store on first request, and redirects to
// the cached object on subsequent ones.
package imgcache

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"image"
	"image/jpeg"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"

	"github.com/disintegration/imaging"
	"github.com/neoworks/otter/internal/blobcache"
	"github.com/neoworks/otter/internal/storage"

	// Register image decoders so downscaleThumbnail can decode common formats.
	_ "image/gif"
	_ "image/png"

	_ "golang.org/x/image/webp"
)

const keyPrefix = "img-cache/"

// EvictionSource describes the image cache to the blob cache evictor: every
// image stands alone, and an evicted one is fetched again on its next request.
func EvictionSource() blobcache.Source {
	return blobcache.Files(keyPrefix)
}

// Thumbnail downscale bounds: oversized images are shrunk to this width and
// re-encoded as JPEG to cap stored size. Anything already narrower is kept as-is.
const (
	thumbMaxWidth    = 640
	thumbJPEGQuality = 80
)

// Some CDNs hotlink-check against the embedding site rather than their own host,
// so a referer of the CDN origin still 403s. Map those CDNs to the site origin
// their protection expects.
var cdnReferer = map[string]string{
	"fourhoi.com": "https://missav.ws/",
}

type Handler struct {
	store *storage.Client
}

func New(store *storage.Client) *Handler {
	return &Handler{store: store}
}

// ServeHTTP handles GET /api/img?url=<remote>. It always ends in a redirect: to
// the cached blob on success, or back to the original URL on failure.
func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	raw := r.URL.Query().Get("url")
	remote, err := url.Parse(raw)
	if err != nil || (remote.Scheme != "http" && remote.Scheme != "https") {
		http.Error(w, "invalid url", http.StatusBadRequest)
		return
	}

	key := keyPrefix + hashURL(raw)
	ctx := r.Context()

	// Touching marks a hit as used for cache eviction; it fails when not cached.
	if h.store.Touch(ctx, key) != nil {
		if err := h.fetchAndStore(ctx, remote, key); err != nil {
			log.Printf("imgcache %q: %v", raw, err)
			// Best effort: let the browser try the origin directly.
			http.Redirect(w, r, raw, http.StatusFound)
			return
		}
	}

	// The blob may be evicted once unused for blobcache.RecentUseGrace (touches
	// are throttled), so the redirect to it is remembered well under that.
	w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(blobcache.RecentUseGrace.Seconds()/2)))
	http.Redirect(w, r, storage.RelativeURL(key), http.StatusFound)
}

// Prefetch warms the cache for a remote image ahead of any browser request, so
// the first view of a persisted result is served straight from the blob store. It shares
// the redirect handler's cache key, and is a no-op when already cached.
func (h *Handler) Prefetch(ctx context.Context, raw string) error {
	remote, err := url.Parse(raw)
	if err != nil || (remote.Scheme != "http" && remote.Scheme != "https") {
		return fmt.Errorf("invalid url %q", raw)
	}
	key := keyPrefix + hashURL(raw)
	if h.store.Touch(ctx, key) == nil {
		return nil
	}
	return h.fetchAndStore(ctx, remote, key)
}

func (h *Handler) fetchAndStore(ctx context.Context, remote *url.URL, key string) error {
	data, contentType, err := fetchImage(ctx, remote)
	if err != nil {
		return err
	}
	data, contentType = downscaleThumbnail(data, contentType)
	return h.store.Put(ctx, key, bytes.NewReader(data), int64(len(data)), contentType)
}

func fetchImage(ctx context.Context, remote *url.URL) ([]byte, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, remote.String(), nil)
	if err != nil {
		return nil, "", err
	}
	// Hotlink protection keys on User-Agent + Referer; present as a real browser
	// loading the image from its own origin.
	req.Header.Set("User-Agent",
		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
	req.Header.Set("Referer", refererFor(remote))
	req.Header.Set("Accept", "image/avif,image/webp,image/*,video/*,*/*;q=0.8")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, "", &statusError{code: resp.StatusCode}
	}

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, "", err
	}
	contentType := resp.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "image/jpeg"
	}
	return data, contentType, nil
}

// downscaleThumbnail shrinks oversized images to thumbMaxWidth and re-encodes as
// JPEG to cap stored size. It returns the input unchanged when the bytes cannot
// be decoded or the image is already narrow enough.
func downscaleThumbnail(data []byte, contentType string) ([]byte, string) {
	source, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return data, contentType
	}
	if source.Bounds().Dx() <= thumbMaxWidth {
		return data, contentType
	}

	resized := imaging.Resize(source, thumbMaxWidth, 0, imaging.Lanczos)
	var buffer bytes.Buffer
	if err := jpeg.Encode(&buffer, resized, &jpeg.Options{Quality: thumbJPEGQuality}); err != nil {
		return data, contentType
	}
	return buffer.Bytes(), "image/jpeg"
}

// refererFor returns the referer a CDN's hotlink protection expects: the
// embedding site origin for known CDNs, otherwise the asset's own origin.
func refererFor(remote *url.URL) string {
	host := strings.TrimPrefix(remote.Host, "www.")
	for cdn, referer := range cdnReferer {
		if host == cdn || strings.HasSuffix(host, "."+cdn) {
			return referer
		}
	}
	return remote.Scheme + "://" + remote.Host + "/"
}

func hashURL(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

type statusError struct{ code int }

func (e *statusError) Error() string {
	return "upstream status " + http.StatusText(e.code)
}
