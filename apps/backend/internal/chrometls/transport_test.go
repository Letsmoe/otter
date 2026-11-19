package chrometls

import (
	"context"
	"crypto/x509"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

func dialer() DialFunc {
	return (&net.Dialer{Timeout: 10 * time.Second}).DialContext
}

// TestHandshakeLooksLikeChrome asks a fingerprinting service what it saw.
// Opt-in: OTTER_LIVE_TLS=1 (needs the internet).
func TestHandshakeLooksLikeChrome(t *testing.T) {
	if os.Getenv("OTTER_LIVE_TLS") == "" {
		t.Skip("set OTTER_LIVE_TLS=1 to check the handshake against tls.peet.ws")
	}
	client := &http.Client{Transport: New(dialer()), Timeout: 20 * time.Second}
	response, err := client.Get("https://tls.peet.ws/api/all")
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	var seen struct {
		HTTPVersion string `json:"http_version"`
		TLS         struct {
			JA4 string `json:"ja4"`
		} `json:"tls"`
	}
	if err := json.NewDecoder(response.Body).Decode(&seen); err != nil {
		t.Fatal(err)
	}
	t.Logf("http %s, ja4 %s", seen.HTTPVersion, seen.TLS.JA4)
	if seen.HTTPVersion != "h2" {
		t.Errorf("http version %q, want h2 like Chrome", seen.HTTPVersion)
	}
	// Chrome: TLS 1.3, SNI, ALPN h2 ("t13d…h2"); Go's own client offers fewer ciphers and extensions.
	if !strings.HasPrefix(seen.TLS.JA4, "t13d") || !strings.Contains(seen.TLS.JA4, "h2_") {
		t.Errorf("ja4 %q does not look like Chrome", seen.TLS.JA4)
	}
}

// echoServer answers with the protocol and body it got; http2 decides whether it offers HTTP/2.
func echoServer(t *testing.T, http2 bool) *httptest.Server {
	t.Helper()
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		_, _ = writer.Write([]byte(request.Proto + " " + string(body)))
	}))
	server.EnableHTTP2 = http2
	server.StartTLS()
	t.Cleanup(server.Close)
	return server
}

func trustingTransport(server *httptest.Server) *Transport {
	transport := New(dialer())
	transport.roots = x509.NewCertPool()
	transport.roots.AddCert(server.Certificate())
	return transport
}

func post(t *testing.T, transport *Transport, url string) string {
	t.Helper()
	request, _ := http.NewRequestWithContext(context.Background(), http.MethodPost, url, strings.NewReader("hello"))
	response, err := (&http.Client{Transport: transport}).Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	answer, _ := io.ReadAll(response.Body)
	return string(answer)
}

func TestSpeaksHTTP2WhereOffered(t *testing.T) {
	server := echoServer(t, true)
	if answer := post(t, trustingTransport(server), server.URL); answer != "HTTP/2.0 hello" {
		t.Fatalf("got %q", answer)
	}
}

func TestFallsBackToHTTP1WithTheBodyIntact(t *testing.T) {
	server := echoServer(t, false)
	transport := trustingTransport(server)
	if answer := post(t, transport, server.URL); answer != "HTTP/1.1 hello" {
		t.Fatalf("got %q", answer)
	}
	if !transport.usesHTTP1(strings.TrimPrefix(server.URL, "https://")) {
		t.Fatal("the HTTP/1.1 host was not remembered")
	}
	if answer := post(t, transport, server.URL); answer != "HTTP/1.1 hello" {
		t.Fatalf("second request got %q", answer)
	}
}
