// Package challenge lets the phone app solve sites' bot checks (Cloudflare's
// "Just a moment" page, Turnstile) in a WebView, the way FlareSolverr does with
// a headless browser. The app earns the clearance cookies; plugin requests to
// that site send them along with the WebView's user agent.
//
// A clearance only holds from the IP and user agent that earned it. The app's
// WebView and the embedded backend run in one app, so a system VPN routes
// both the same way.
package challenge

import (
	"bytes"
	"net/http"
	"net/url"
	"strings"
)

// IsChallenge reports whether a response is a bot check instead of the page.
func IsChallenge(status int, header http.Header, body []byte) bool {
	if strings.EqualFold(header.Get("Cf-Mitigated"), "challenge") {
		return true
	}
	if status != http.StatusForbidden && status != http.StatusServiceUnavailable {
		return false
	}
	if !strings.Contains(strings.ToLower(header.Get("Server")), "cloudflare") {
		return false
	}
	return looksLikeChallengePage(body)
}

func looksLikeChallengePage(body []byte) bool {
	markers := [][]byte{[]byte("challenge-platform"), []byte("<title>Just a moment"), []byte("cf-turnstile")}
	for _, marker := range markers {
		if bytes.Contains(body, marker) {
			return true
		}
	}
	return false
}

// DomainOf is the host a clearance belongs to, without "www.".
func DomainOf(rawURL string) string {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return ""
	}
	return strings.TrimPrefix(strings.ToLower(parsed.Hostname()), "www.")
}
