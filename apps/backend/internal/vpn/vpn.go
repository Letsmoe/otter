// Package vpn tells whether the backend's traffic leaves through a VPN, and
// dials connections that refuse to leave any other way.
package vpn

import (
	"net"
	"net/netip"
	"strings"

	"github.com/wlynxg/anet"
)

var vpnPrefixes = []string{"tun", "wg", "ppp", "proton", "nordlynx", "mullvad"}

// probeAddress stands for "the internet": looking up its route sends nothing.
var probeAddress = netip.MustParseAddr("1.1.1.1")

// Status is how the backend's traffic to the internet leaves the device.
type Status struct {
	// Connected means the traffic goes through a VPN interface.
	Connected bool
	// Bypassed means a VPN interface is up, but this app's traffic skips it,
	// e.g. because the VPN's split tunnelling excludes Otter.
	Bypassed bool
	// Interface names the VPN interface that is up, if any.
	Interface string
}

// host is the operating system's view of interfaces and routes; tests replace it.
var host = system{
	interfaces:  anet.Interfaces,
	addresses:   anet.InterfaceAddrsByInterface,
	routeSource: routeSource,
}

type system struct {
	interfaces  func() ([]net.Interface, error)
	addresses   func(*net.Interface) ([]net.Addr, error)
	routeSource func(destination netip.Addr) (netip.Addr, error)
}

// Check reports whether traffic to the internet would go through a VPN right now.
func Check() Status {
	interfaces, err := host.interfaces()
	if err != nil {
		return Status{}
	}
	up, name := vpnInterface(interfaces)
	if !up {
		return Status{}
	}
	if routesThroughVPN(probeAddress) {
		return Status{Connected: true, Interface: name}
	}
	return Status{Bypassed: true, Interface: name}
}

// IsConnected reports whether traffic to the internet goes through a VPN.
func IsConnected() bool {
	return Check().Connected
}

func vpnInterface(interfaces []net.Interface) (connected bool, iface string) {
	for _, candidate := range interfaces {
		if isVPNInterface(candidate) {
			return true, candidate.Name
		}
	}
	return false, ""
}

func isVPNInterface(candidate net.Interface) bool {
	if candidate.Flags&net.FlagUp == 0 {
		return false
	}
	for _, prefix := range vpnPrefixes {
		if strings.HasPrefix(candidate.Name, prefix) {
			return true
		}
	}
	return false
}

// routesThroughVPN asks the system which source address it would use to
// reach destination (the routing decision, made for this app's own traffic)
// and whether that address belongs to a VPN interface.
func routesThroughVPN(destination netip.Addr) bool {
	source, err := host.routeSource(destination)
	if err != nil {
		return false
	}
	return isVPNAddress(source)
}

// routeSource "connects" a UDP socket, which only looks up the route: no packet is sent.
func routeSource(destination netip.Addr) (netip.Addr, error) {
	connection, err := net.DialUDP("udp", nil, net.UDPAddrFromAddrPort(netip.AddrPortFrom(destination, 443)))
	if err != nil {
		return netip.Addr{}, err
	}
	defer connection.Close()
	return connection.LocalAddr().(*net.UDPAddr).AddrPort().Addr().Unmap(), nil
}

// isVPNAddress reports whether address is assigned to a VPN interface that is up.
func isVPNAddress(address netip.Addr) bool {
	interfaces, err := host.interfaces()
	if err != nil {
		return false
	}
	for index := range interfaces {
		if isVPNInterface(interfaces[index]) && hasAddress(&interfaces[index], address) {
			return true
		}
	}
	return false
}

func hasAddress(candidate *net.Interface, address netip.Addr) bool {
	addresses, err := host.addresses(candidate)
	if err != nil {
		return false
	}
	for _, assigned := range addresses {
		prefix, err := netip.ParsePrefix(assigned.String())
		if err == nil && prefix.Addr().Unmap() == address {
			return true
		}
	}
	return false
}
