package challenge

import (
	"context"
	"errors"
	"net/http"
	"path/filepath"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/cache"
	"github.com/Letsmoe/otter/internal/db"
)

func newTestService(t *testing.T) *Service {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() { database.Close() })
	return New(ctx, cache.New(ctx, database))
}

// subscribe connects a fake app until the test ends.
func subscribe(t *testing.T, service *Service) <-chan Request {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	return service.Subscribe(ctx)
}

func receive(t *testing.T, requests <-chan Request) Request {
	t.Helper()
	select {
	case request := <-requests:
		return request
	case <-time.After(2 * time.Second):
		t.Fatal("the app got no challenge")
		return Request{}
	}
}

func TestIsChallenge(t *testing.T) {
	cloudflare := http.Header{"Server": {"cloudflare"}}
	cases := []struct {
		name   string
		status int
		header http.Header
		body   string
		want   bool
	}{
		{"mitigated header", 403, http.Header{"Cf-Mitigated": {"challenge"}}, "", true},
		{"just a moment page", 503, cloudflare, "<html><title>Just a moment...</title>", true},
		{"turnstile page", 403, cloudflare, `<div class="cf-turnstile">`, true},
		{"plain forbidden from cloudflare", 403, cloudflare, "Access denied", false},
		{"ok page mentioning it", 200, cloudflare, "<title>Just a moment</title>", false},
		{"forbidden elsewhere", 403, http.Header{"Server": {"nginx"}}, "challenge-platform", false},
	}
	for _, testCase := range cases {
		got := IsChallenge(testCase.status, testCase.header, []byte(testCase.body))
		if got != testCase.want {
			t.Errorf("%s: IsChallenge = %v, want %v", testCase.name, got, testCase.want)
		}
	}
}

func TestSolveWithoutAppFailsAndRemembersTheSite(t *testing.T) {
	service := newTestService(t)
	_, err := service.Solve(context.Background(), "https://www.example.org/page")
	if !errors.Is(err, ErrNoSolver) {
		t.Fatalf("err = %v, want ErrNoSolver", err)
	}
	if !service.NeedsSolving("example.org") {
		t.Fatal("the site that showed a check is not remembered")
	}
}

func TestSolveWaitsForTheAppAndStoresTheClearance(t *testing.T) {
	service := newTestService(t)
	requests := subscribe(t, service)
	result := make(chan error, 1)
	go func() {
		_, err := service.Solve(context.Background(), "https://example.org/a")
		result <- err
	}()
	request := receive(t, requests)
	if request.Interactive {
		t.Fatal("the first attempt should be hidden")
	}
	if err := service.Complete(request.ID, "cf_clearance=abc", "WebView UA"); err != nil {
		t.Fatalf("complete: %v", err)
	}
	if err := <-result; err != nil {
		t.Fatalf("solve: %v", err)
	}
	clearance, found := service.Clearance("https://example.org/other")
	if !found || clearance.Cookies != "cf_clearance=abc" || clearance.UserAgent != "WebView UA" {
		t.Fatalf("clearance = %+v, %v", clearance, found)
	}
}

func TestOneAttemptPerSiteAtATime(t *testing.T) {
	service := newTestService(t)
	requests := subscribe(t, service)
	first := service.enqueue("https://example.org/a", false)
	second := service.enqueue("https://www.example.org/b", false)
	if first != second {
		t.Fatal("a second check of the same site started another attempt")
	}
	receive(t, requests)
	select {
	case extra := <-requests:
		t.Fatalf("the app was asked twice: %+v", extra)
	default:
	}
}

func TestFailedHiddenAttemptIsShownOnlyToSomeoneWaiting(t *testing.T) {
	service := newTestService(t)
	requests := subscribe(t, service)

	background := make(chan error, 1)
	go func() {
		_, err := service.Solve(context.Background(), "https://example.org/a")
		background <- err
	}()
	service.Fail(receive(t, requests).ID, "wants a click")
	if err := <-background; !errors.Is(err, ErrUnsolved) {
		t.Fatalf("background err = %v, want ErrUnsolved", err)
	}

	playing := make(chan error, 1)
	go func() {
		_, err := service.Solve(WithInteractive(context.Background()), "https://example.org/a")
		playing <- err
	}()
	service.Fail(receive(t, requests).ID, "wants a click")
	shown := receive(t, requests)
	if !shown.Interactive {
		t.Fatal("the second attempt for someone playing should be shown")
	}
	if err := service.Complete(shown.ID, "cf_clearance=xyz", "WebView UA"); err != nil {
		t.Fatalf("complete: %v", err)
	}
	if err := <-playing; err != nil {
		t.Fatalf("playing err = %v", err)
	}
}
