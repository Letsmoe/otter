package tmdb

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestReachableFollowsTMDBAnswers(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	client := NewClient(func() (string, string) { return "", "en-US" }).WithBaseURL(server.URL)
	if !client.Reachable() {
		t.Fatal("before any request: unreachable, want reachable")
	}
	if !client.CheckConnection(context.Background()) {
		t.Fatal("TMDB answering 401: unreachable, want reachable")
	}

	server.Close()
	if client.CheckConnection(context.Background()) {
		t.Fatal("TMDB down: reachable, want unreachable")
	}
	if client.Reachable() {
		t.Fatal("after a failed check: reachable, want unreachable")
	}
}

func TestCancelledRequestKeepsReachability(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	defer server.Close()
	client := NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)

	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	var out map[string]any
	_ = client.get(cancelled, "/movie/1", nil, &out)
	if !client.Reachable() {
		t.Fatal("cancelled request marked TMDB unreachable")
	}
}
