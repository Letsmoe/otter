// Package netdns points Go's DNS resolver at explicit servers. Pure-Go builds
// on Android can't find the system resolver (there is no /etc/resolv.conf),
// so the phone app passes the device's DNS servers in instead.
package netdns

import (
	"context"
	"errors"
	"net"
	"os"
	"strings"
	"syscall"
	"time"
)

// dialTimeout bounds each attempt to reach one DNS server.
const dialTimeout = 5 * time.Second

// Configure makes net.DefaultResolver query explicit DNS servers, trying them
// in order: those listed in file (re-read on every lookup, so the phone app
// can rewrite it when the network changes), else the comma-separated servers
// ("192.168.1.1,1.1.1.1"; port 53 unless given). With neither set the
// platform default stays.
func Configure(servers, file string) {
	fixed := parseServers(servers)
	if len(fixed) == 0 && file == "" {
		return
	}
	configured = func() []string { return currentServers(file, fixed) }
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			return dialFirst(ctx, network, configured(), nil)
		},
	}
}

// configured lists the servers set by Configure; nil keeps the platform's.
var configured func() []string

// Control vets a socket before it connects (see net.Dialer.ControlContext).
type Control func(ctx context.Context, network, address string, raw syscall.RawConn) error

// Dial connects to a DNS server like net.DefaultResolver does, with control
// checking each socket before it connects. address is the server the Go
// resolver picked, used when Configure set none.
func Dial(ctx context.Context, network, address string, control Control) (net.Conn, error) {
	servers := []string{address}
	if configured != nil {
		servers = configured()
	}
	return dialFirst(ctx, network, servers, control)
}

// currentServers returns the servers listed in file, else fixed.
func currentServers(file string, fixed []string) []string {
	if file == "" {
		return fixed
	}
	content, err := os.ReadFile(file)
	if err != nil {
		return fixed
	}
	fromFile := parseServers(strings.ReplaceAll(string(content), "\n", ","))
	if len(fromFile) == 0 {
		return fixed
	}
	return fromFile
}

// parseServers splits the list and adds the default port where missing.
func parseServers(servers string) []string {
	var addresses []string
	for server := range strings.SplitSeq(servers, ",") {
		server = strings.TrimSpace(server)
		if server == "" {
			continue
		}
		if _, _, err := net.SplitHostPort(server); err != nil {
			server = net.JoinHostPort(server, "53")
		}
		addresses = append(addresses, server)
	}
	return addresses
}

// dialFirst connects to the first server that answers.
func dialFirst(ctx context.Context, network string, addresses []string, control Control) (net.Conn, error) {
	if len(addresses) == 0 {
		return nil, errors.New("no DNS servers configured")
	}
	dialer := net.Dialer{Timeout: dialTimeout, ControlContext: control}
	var errs []error
	for _, address := range addresses {
		conn, err := dialer.DialContext(ctx, network, address)
		if err == nil {
			return conn, nil
		}
		errs = append(errs, err)
	}
	return nil, errors.Join(errs...)
}
