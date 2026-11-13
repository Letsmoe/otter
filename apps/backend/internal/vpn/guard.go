package vpn

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"syscall"
	"time"

	"github.com/Letsmoe/otter/internal/netdns"
)

// ErrBypassed refuses a connection that would not go through the VPN.
var ErrBypassed = errors.New("VPN required: the connection would not go through the VPN")

// required reports whether guarded connections must go through the VPN ("Require VPN").
var required = func() bool { return false }

// Require sets when guarded connections must go through the VPN; call it once at start.
func Require(isRequired func() bool) {
	required = isRequired
}

// guardedResolver looks up names for guarded connections; its DNS queries
// are vetted like the connections, so a lookup can't reveal a site either.
var guardedResolver = &net.Resolver{
	PreferGo: true,
	Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
		return netdns.Dial(ctx, network, address, controlSocket)
	},
}

// DialContext connects like net.Dialer, but while the VPN is required only
// through it. Each socket's route is checked right before it connects, so no
// packet leaves when the VPN is gone; the connected socket is checked again.
// Use it for everything that talks to streaming sites.
func DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	dialer := &net.Dialer{
		Timeout:        30 * time.Second,
		KeepAlive:      30 * time.Second,
		ControlContext: controlSocket,
		Resolver:       guardedResolver,
	}
	connection, err := dialer.DialContext(ctx, network, address)
	if err != nil {
		return nil, err
	}
	if err := checkConnected(connection); err != nil {
		connection.Close()
		return nil, err
	}
	return connection, nil
}

// controlSocket runs after a socket is made and before it connects.
func controlSocket(_ context.Context, _ string, address string, _ syscall.RawConn) error {
	if !required() {
		return nil
	}
	return checkDestination(address)
}

func checkDestination(address string) error {
	destination, err := netip.ParseAddrPort(address)
	if err != nil {
		return ErrBypassed
	}
	if destination.Addr().Unmap().IsLoopback() {
		return nil
	}
	if !routesThroughVPN(destination.Addr().Unmap()) {
		return ErrBypassed
	}
	return nil
}

// checkConnected catches a route that changed between the check and the connect.
func checkConnected(connection net.Conn) error {
	if !required() {
		return nil
	}
	remote, err := netip.ParseAddrPort(connection.RemoteAddr().String())
	if err == nil && remote.Addr().Unmap().IsLoopback() {
		return nil
	}
	local, err := netip.ParseAddrPort(connection.LocalAddr().String())
	if err != nil || !isVPNAddress(local.Addr().Unmap()) {
		return ErrBypassed
	}
	return nil
}
