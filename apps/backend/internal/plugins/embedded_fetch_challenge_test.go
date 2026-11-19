package plugins

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"github.com/Letsmoe/otter/internal/cache"
	"github.com/Letsmoe/otter/internal/challenge"
	"github.com/Letsmoe/otter/internal/db"
)

// A site behind a bot check: the page only comes with the cookie and the user agent it was earned with.
func checkedSite() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		cookie, err := request.Cookie("cf_clearance")
		if err == nil && cookie.Value == "earned" && request.UserAgent() == "WebView UA" {
			_, _ = writer.Write([]byte("the page"))
			return
		}
		writer.Header().Set("Server", "cloudflare")
		writer.WriteHeader(http.StatusForbidden)
		_, _ = writer.Write([]byte("<title>Just a moment...</title>"))
	}))
}

func newChallengeService(t *testing.T) *challenge.Service {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() { database.Close() })
	return challenge.New(ctx, cache.New(ctx, database))
}

// solveLikeTheApp answers every check the way the phone app's WebView would.
func solveLikeTheApp(t *testing.T, service *challenge.Service) {
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	requests := service.Subscribe(ctx)
	go func() {
		for request := range requests {
			_ = service.Complete(request.ID, "cf_clearance=earned", "WebView UA")
		}
	}()
}

func TestFetchPassesBotCheckThroughTheApp(t *testing.T) {
	site := checkedSite()
	defer site.Close()
	service := newChallengeService(t)
	solveLikeTheApp(t, service)
	fetcher := newFetcher()
	fetcher.challenges = service

	request := fetchRequest{url: site.URL + "/page", method: http.MethodGet, headers: map[string]string{"User-Agent": "plugin UA"}}
	response, err := fetcher.do(context.Background(), request)
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if response.status != http.StatusOK || string(response.body) != "the page" {
		t.Fatalf("got %d %q, want the page", response.status, response.body)
	}
}

func TestFetchWithoutAppReturnsTheCheckPage(t *testing.T) {
	site := checkedSite()
	defer site.Close()
	fetcher := newFetcher()
	fetcher.challenges = newChallengeService(t)

	response, err := fetcher.do(context.Background(), fetchRequest{url: site.URL, method: http.MethodGet, headers: map[string]string{}})
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if response.status != http.StatusForbidden {
		t.Fatalf("status = %d, want the check page's 403", response.status)
	}
}
