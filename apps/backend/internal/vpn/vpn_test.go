package vpn

import (
	"net"
	"testing"
)

func TestVpnInterfaceFindsUpTunnels(t *testing.T) {
	interfaces := []net.Interface{
		{Name: "wlan0", Flags: net.FlagUp},
		{Name: "tun1", Flags: 0},
		{Name: "tun0", Flags: net.FlagUp},
	}
	connected, name := vpnInterface(interfaces)
	if !connected || name != "tun0" {
		t.Fatalf("vpnInterface = %v, %q", connected, name)
	}
}

func TestVpnInterfaceWithoutTunnel(t *testing.T) {
	if connected, _ := vpnInterface([]net.Interface{{Name: "wlan0", Flags: net.FlagUp}}); connected {
		t.Fatal("wlan0 counted as VPN")
	}
}
