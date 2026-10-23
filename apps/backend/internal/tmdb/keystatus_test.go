package tmdb

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestKeyStatusFollowsTMDBAnswers(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("api_key") != "good" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Write([]byte(`{}`))
	}))
	defer server.Close()

	key := ""
	client := NewClient(func() (string, string) { return key, "en-US" }).WithBaseURL(server.URL)
	if status := client.KeyStatus(); status != KeyStatusMissing {
		t.Fatalf("no key: status %q, want missing", status)
	}

	key = "revoked"
	var out map[string]any
	if err := client.get(context.Background(), "/movie/1", nil, &out); !errors.Is(err, ErrInvalidKey) {
		t.Fatalf("revoked key: err %v, want ErrInvalidKey", err)
	}
	if status := client.KeyStatus(); status != KeyStatusInvalid {
		t.Fatalf("revoked key: status %q, want invalid", status)
	}

	key = "good"
	if status := client.KeyStatus(); status != KeyStatusOK {
		t.Fatalf("new key: status %q, want ok", status)
	}
	if valid, err := client.CheckKey(context.Background(), "revoked"); err != nil || valid {
		t.Fatalf("CheckKey(revoked) = %v, %v; want false, nil", valid, err)
	}
	if valid, err := client.CheckKey(context.Background(), "good"); err != nil || !valid {
		t.Fatalf("CheckKey(good) = %v, %v; want true, nil", valid, err)
	}
}
