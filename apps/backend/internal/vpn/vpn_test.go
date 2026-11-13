package vpn

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"testing"
)

var (
	wifiAddress   = netip.MustParseAddr("192.168.1.20")
	tunnelAddress = netip.MustParseAddr("10.2.0.2")
)

// fakeHost is a phone with Wi-Fi and, when tunnelUp, a VPN tunnel; routes
// leave from routeVia.
func fakeHost(t *testing.T, tunnelUp bool, routeVia netip.Addr) {
	t.Helper()
	interfaces := []net.Interface{{Index: 1, Name: "wlan0", Flags: net.FlagUp}}
	if tunnelUp {
		interfaces = append(interfaces, net.Interface{Index: 2, Name: "tun0", Flags: net.FlagUp})
	}
	addressesByName := map[string]string{"wlan0": "192.168.1.20/24", "tun0": "10.2.0.2/32"}
	original := host
	host = system{
		interfaces: func() ([]net.Interface, error) { return interfaces, nil },
		addresses: func(candidate *net.Interface) ([]net.Addr, error) {
			prefix := netip.MustParsePrefix(addressesByName[candidate.Name])
			return []net.Addr{&net.IPNet{IP: prefix.Addr().AsSlice(), Mask: net.CIDRMask(prefix.Bits(), 32)}}, nil
		},
		routeSource: func(netip.Addr) (netip.Addr, error) { return routeVia, nil },
	}
	t.Cleanup(func() { host = original })
}

func requireVPN(t *testing.T) {
	t.Helper()
	Require(func() bool { return true })
	t.Cleanup(func() { Require(func() bool { return false }) })
}

func TestTrafficThroughTheTunnelIsConnected(t *testing.T) {
	fakeHost(t, true, tunnelAddress)
	status := Check()
	if !status.Connected || status.Bypassed || status.Interface != "tun0" {
		t.Fatalf("status = %+v", status)
	}
}

func TestAnExcludedAppIsBypassed(t *testing.T) {
	// Split tunnelling: tun0 is up, but this app's route leaves over Wi-Fi.
	fakeHost(t, true, wifiAddress)
	status := Check()
	if status.Connected || !status.Bypassed {
		t.Fatalf("status = %+v, want bypassed", status)
	}
}

func TestNoTunnelIsNeitherConnectedNorBypassed(t *testing.T) {
	fakeHost(t, false, wifiAddress)
	if status := Check(); status.Connected || status.Bypassed {
		t.Fatalf("status = %+v", status)
	}
}

func TestADownTunnelDoesNotCount(t *testing.T) {
	interfaces := []net.Interface{{Name: "wlan0", Flags: net.FlagUp}, {Name: "tun1"}}
	if connected, _ := vpnInterface(interfaces); connected {
		t.Fatal("a tunnel that is down counted as VPN")
	}
}

func TestConnectionsOutsideTheTunnelAreRefusedBeforeConnecting(t *testing.T) {
	fakeHost(t, true, wifiAddress)
	requireVPN(t)
	if err := controlSocket(context.Background(), "tcp", "203.0.113.7:443", nil); !errors.Is(err, ErrBypassed) {
		t.Fatalf("control = %v, want ErrBypassed", err)
	}
}

func TestDNSToTheHomeRouterIsRefusedToo(t *testing.T) {
	// The router forwards lookups to the ISP, which would learn every site's name.
	fakeHost(t, false, wifiAddress)
	requireVPN(t)
	if err := controlSocket(context.Background(), "udp", "192.168.1.1:53", nil); !errors.Is(err, ErrBypassed) {
		t.Fatalf("control = %v, want ErrBypassed", err)
	}
}

func TestConnectionsThroughTheTunnelPass(t *testing.T) {
	fakeHost(t, true, tunnelAddress)
	requireVPN(t)
	if err := controlSocket(context.Background(), "tcp", "203.0.113.7:443", nil); err != nil {
		t.Fatalf("control = %v", err)
	}
}

func TestNothingIsCheckedWhileTheVPNIsNotRequired(t *testing.T) {
	fakeHost(t, false, wifiAddress)
	if err := controlSocket(context.Background(), "tcp", "203.0.113.7:443", nil); err != nil {
		t.Fatalf("control = %v", err)
	}
}

func TestLoopbackStaysReachableWithoutAVPN(t *testing.T) {
	fakeHost(t, false, wifiAddress)
	requireVPN(t)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {}))
	defer server.Close()
	connection, err := DialContext(context.Background(), "tcp", server.Listener.Addr().String())
	if err != nil {
		t.Fatalf("dialing loopback: %v", err)
	}
	connection.Close()
}

func TestAConnectionThatLandedOutsideTheTunnelIsClosed(t *testing.T) {
	// The route changed between the check and the connect: the socket's own address tells.
	fakeHost(t, true, tunnelAddress)
	requireVPN(t)
	connection := fakeConnection{local: "192.168.1.20:51000", remote: "203.0.113.7:443"}
	if err := checkConnected(connection); !errors.Is(err, ErrBypassed) {
		t.Fatalf("checkConnected = %v, want ErrBypassed", err)
	}
	connection.local = "10.2.0.2:51000"
	if err := checkConnected(connection); err != nil {
		t.Fatalf("checkConnected through the tunnel = %v", err)
	}
}

func TestTheRouteLookupNamesALocalAddress(t *testing.T) {
	source, err := routeSource(probeAddress)
	if err != nil {
		t.Skipf("no route to the internet here: %v", err)
	}
	if !source.IsValid() || source.IsUnspecified() {
		t.Fatalf("route source = %v", source)
	}
}

type fakeConnection struct {
	net.Conn
	local  string
	remote string
}

func (connection fakeConnection) LocalAddr() net.Addr {
	return net.TCPAddrFromAddrPort(netip.MustParseAddrPort(connection.local))
}

func (connection fakeConnection) RemoteAddr() net.Addr {
	return net.TCPAddrFromAddrPort(netip.MustParseAddrPort(connection.remote))
}
