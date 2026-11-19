// Package chrometls makes HTTPS requests with Chrome's TLS handshake. Sites
// behind Cloudflare tell Go's own ClientHello (its JA3/JA4 fingerprint) from a
// browser's and may refuse it, or refuse a clearance cookie the app's WebView
// earned when it comes back over a handshake no browser makes.
//
// uTLS sends Chrome's ClientHello, which offers HTTP/2 like Chrome does: hosts
// that pick HTTP/2 are served by an http2.Transport, the others are remembered
// and served over HTTP/1.1.
package chrometls

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"errors"
	"net"
	"net/http"
	"sync"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

// DialFunc opens the TCP connection a handshake runs over, e.g. vpn.DialContext.
type DialFunc func(ctx context.Context, network, address string) (net.Conn, error)

// errHTTP1 means the server picked HTTP/1.1 from Chrome's ALPN offer.
var errHTTP1 = errors.New("chrometls: the server chose HTTP/1.1")

// errSwitchedToHTTP2 means a host known for HTTP/1.1 now picks HTTP/2.
var errSwitchedToHTTP2 = errors.New("chrometls: the server switched to HTTP/2")

// Transport is an http.RoundTripper whose HTTPS connections greet like Chrome.
type Transport struct {
	dial  DialFunc
	http2 *http2.Transport
	http1 *http.Transport

	mutex      sync.Mutex
	http1Hosts map[string]bool
	// roots replaces the system's CA pool; tests trust their own server with it.
	roots *x509.CertPool
}

// New returns a transport dialing through dial.
func New(dial DialFunc) *Transport {
	transport := &Transport{dial: dial, http1Hosts: map[string]bool{}}
	transport.http2 = &http2.Transport{
		DialTLSContext: func(ctx context.Context, network, address string, _ *tls.Config) (net.Conn, error) {
			return transport.dialHTTP2(ctx, network, address)
		},
		ReadIdleTimeout: 30 * time.Second,
	}
	transport.http1 = &http.Transport{
		DialContext:           dial,
		DialTLSContext:        transport.dialHTTP1,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		ExpectContinueTimeout: time.Second,
	}
	return transport
}

// RoundTrip sends plain HTTP and known HTTP/1.1 hosts over HTTP/1.1, everything else over HTTP/2.
func (t *Transport) RoundTrip(request *http.Request) (*http.Response, error) {
	if request.URL.Scheme != "https" || t.usesHTTP1(request.URL.Host) {
		return t.roundTripHTTP1(request)
	}
	response, err := t.http2.RoundTrip(request)
	if !errors.Is(err, errHTTP1) {
		return response, err
	}
	t.setHTTP1(request.URL.Host, true)
	return t.http1.RoundTrip(rewound(request))
}

func (t *Transport) roundTripHTTP1(request *http.Request) (*http.Response, error) {
	response, err := t.http1.RoundTrip(request)
	if !errors.Is(err, errSwitchedToHTTP2) {
		return response, err
	}
	t.setHTTP1(request.URL.Host, false)
	return t.http2.RoundTrip(rewound(request))
}

// rewound is the request with a fresh body, for sending it again after a failed dial.
func rewound(request *http.Request) *http.Request {
	if request.Body == nil || request.GetBody == nil {
		return request
	}
	body, err := request.GetBody()
	if err != nil {
		return request
	}
	again := request.Clone(request.Context())
	again.Body = body
	return again
}

func (t *Transport) usesHTTP1(host string) bool {
	t.mutex.Lock()
	defer t.mutex.Unlock()
	return t.http1Hosts[host]
}

func (t *Transport) setHTTP1(host string, http1 bool) {
	t.mutex.Lock()
	defer t.mutex.Unlock()
	if http1 {
		t.http1Hosts[host] = true
		return
	}
	delete(t.http1Hosts, host)
}

func (t *Transport) dialHTTP2(ctx context.Context, network, address string) (net.Conn, error) {
	conn, err := t.handshake(ctx, network, address)
	if err != nil {
		return nil, err
	}
	if conn.ConnectionState().NegotiatedProtocol != http2.NextProtoTLS {
		_ = conn.Close()
		return nil, errHTTP1
	}
	return conn, nil
}

func (t *Transport) dialHTTP1(ctx context.Context, network, address string) (net.Conn, error) {
	conn, err := t.handshake(ctx, network, address)
	if err != nil {
		return nil, err
	}
	if conn.ConnectionState().NegotiatedProtocol == http2.NextProtoTLS {
		_ = conn.Close()
		return nil, errSwitchedToHTTP2
	}
	return conn, nil
}

// handshake connects and greets with the ClientHello of a current Chrome.
func (t *Transport) handshake(ctx context.Context, network, address string) (*utls.UConn, error) {
	raw, err := t.dial(ctx, network, address)
	if err != nil {
		return nil, err
	}
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		host = address
	}
	conn := utls.UClient(raw, &utls.Config{ServerName: host, RootCAs: t.roots}, utls.HelloChrome_Auto)
	if err := conn.HandshakeContext(ctx); err != nil {
		_ = raw.Close()
		return nil, err
	}
	return conn, nil
}
