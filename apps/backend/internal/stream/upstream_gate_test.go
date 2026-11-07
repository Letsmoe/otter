package stream

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/Letsmoe/otter/internal/storage"
)

// Downloads remuxed for a TV are read back over loopback; the VPN gate must
// only stop internet traffic, not those reads.
func TestUpstreamGateSkipsOwnBlobs(t *testing.T) {
	mux := http.NewServeMux()
	server := httptest.NewServer(mux)
	defer server.Close()
	store, err := storage.New(t.TempDir(), server.URL)
	if err != nil {
		t.Fatal(err)
	}
	mux.Handle(storage.RoutePrefix, store.Handler())
	if err := store.Put(context.Background(), "downloads/seg0.ts", strings.NewReader("segment"), 7, "video/MP2T"); err != nil {
		t.Fatal(err)
	}

	service := &CacheService{storage: store}
	vpnRequired := errors.New("VPN required")
	service.SetUpstreamGate(func() error { return vpnRequired })

	response, err := service.fetch(context.Background(), store.DirectURL("downloads/seg0.ts"), nil)
	if err != nil {
		t.Fatalf("own blob blocked: %v", err)
	}
	response.Body.Close()
	if _, err := service.fetch(context.Background(), "https://cdn.example/seg0.ts", nil); !errors.Is(err, vpnRequired) {
		t.Fatalf("CDN request: err %v, want the gate's error", err)
	}
}
