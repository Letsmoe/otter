package vpn

import (
	"net"
	"strings"

	"github.com/wlynxg/anet"
)

var vpnPrefixes = []string{"tun", "wg", "ppp", "proton", "nordlynx", "mullvad"}

// IsConnected reports whether a VPN interface is up and its name.
func IsConnected() (connected bool, iface string) {
	// anet works on Android 11+, where net.Interfaces is denied.
	interfaces, err := anet.Interfaces()
	if err != nil {
		return false, ""
	}
	return vpnInterface(interfaces)
}

func vpnInterface(interfaces []net.Interface) (connected bool, iface string) {
	for _, i := range interfaces {
		if i.Flags&net.FlagUp == 0 {
			continue
		}
		for _, prefix := range vpnPrefixes {
			if strings.HasPrefix(i.Name, prefix) {
				return true, i.Name
			}
		}
	}
	return false, ""
}
