package main

import (
	"net"
	"net/http"
	"strings"
)

// DLNA flags: streaming transfer mode, background transfer, connection stalling.
const dlnaFlags = "DLNA.ORG_FLAGS=01700000000000000000000000000000"

// dlnaHeaders answers the content-features handshake DLNA TVs (Samsung) make
// before playing. Continuous streams can't serve byte ranges (OP=00); files
// can (OP=01), so the TV seeks in them itself.
func dlnaHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("transferMode.dlna.org", "Streaming")
		if strings.HasSuffix(request.URL.Path, "/continuous") {
			writer.Header().Set("contentFeatures.dlna.org", "DLNA.ORG_OP=00;DLNA.ORG_CI=0;"+dlnaFlags)
		} else {
			writer.Header().Set("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;"+dlnaFlags)
		}
		next.ServeHTTP(writer, request)
	})
}

// privateNetworkOnly refuses clients outside loopback and private ranges, so
// the LAN stream port never serves the internet (e.g. over mobile data).
func privateNetworkOnly(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if !isPrivateClient(request.RemoteAddr) {
			http.Error(writer, "forbidden", http.StatusForbidden)
			return
		}
		next.ServeHTTP(writer, request)
	})
}

func isPrivateClient(remoteAddress string) bool {
	host, _, err := net.SplitHostPort(remoteAddress)
	if err != nil {
		return false
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return false
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast()
}
