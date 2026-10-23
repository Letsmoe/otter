package stream

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

// redirectChain builds a server that redirects /a → /b → /final and records the
// Referer header seen on the final hop.
func redirectChain(t *testing.T, finalReferer *string) *httptest.Server {
	t.Helper()
	mux := http.NewServeMux()
	var server *httptest.Server
	mux.HandleFunc("/a", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, server.URL+"/b", http.StatusFound)
	})
	mux.HandleFunc("/b", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, server.URL+"/final", http.StatusFound)
	})
	mux.HandleFunc("/final", func(w http.ResponseWriter, r *http.Request) {
		*finalReferer = r.Header.Get("Referer")
		w.WriteHeader(http.StatusOK)
	})
	server = httptest.NewServer(mux)
	return server
}

func TestRedirectDropsAutoReferer(t *testing.T) {
	var finalReferer string
	server := redirectChain(t, &finalReferer)
	defer server.Close()

	req, err := http.NewRequest(http.MethodGet, server.URL+"/a", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()

	if finalReferer != "" {
		t.Errorf("final hop received auto-injected Referer %q, want none", finalReferer)
	}
}

func TestRedirectKeepsExplicitReferer(t *testing.T) {
	var finalReferer string
	server := redirectChain(t, &finalReferer)
	defer server.Close()

	req, err := http.NewRequest(http.MethodGet, server.URL+"/a", nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Referer", "https://example.com/page")
	resp, err := httpClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()

	if finalReferer != "https://example.com/page" {
		t.Errorf("final hop received Referer %q, want explicit original", finalReferer)
	}
}
