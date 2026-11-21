package main

import (
	"bufio"
	"bytes"
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"time"
)

var ssdpGroup = &net.UDPAddr{IP: net.IPv4(239, 255, 255, 250), Port: 1900}

// The search targets the virtual TV answers to: what a phone looking for a TV asks for.
var searchTargets = []string{
	"upnp:rootdevice",
	"urn:schemas-upnp-org:device:MediaRenderer:1",
	"urn:schemas-upnp-org:service:AVTransport:1",
	"urn:schemas-upnp-org:service:RenderingControl:1",
	"urn:schemas-upnp-org:service:ConnectionManager:1",
}

const notifyEvery = 30 * time.Second

// announcer answers SSDP searches and says "alive" now and then, like a TV that is switched on.
type announcer struct {
	deviceID string
	httpPort int
}

// run listens for searches on every interface: left to the system, the group is joined only on the
// default route's interface, which on a computer with a VPN isn't the one the phone is on.
func (tv *announcer) run() error {
	listeners := 0
	for _, networkInterface := range multicastInterfaces() {
		listener, err := net.ListenMulticastUDP("udp4", &networkInterface, ssdpGroup)
		if err != nil {
			log.Printf("SSDP: not listening on %s: %v", networkInterface.Name, err)
			continue
		}
		listeners++
		go tv.listen(listener)
	}
	if listeners == 0 {
		return fmt.Errorf("joining SSDP on no interface")
	}
	tv.notifyAlive()
	return nil
}

func (tv *announcer) listen(listener *net.UDPConn) {
	buffer := make([]byte, 4096)
	for {
		count, sender, err := listener.ReadFromUDP(buffer)
		if err != nil {
			log.Printf("SSDP: %v", err)
			return
		}
		tv.answerSearch(buffer[:count], sender)
	}
}

// multicastInterfaces are the interfaces that are up and can carry multicast, loopback left out.
func multicastInterfaces() []net.Interface {
	found := []net.Interface{}
	interfaces, err := net.Interfaces()
	if err != nil {
		return found
	}
	for _, networkInterface := range interfaces {
		usable := networkInterface.Flags&net.FlagUp != 0 && networkInterface.Flags&net.FlagMulticast != 0
		if usable && networkInterface.Flags&net.FlagLoopback == 0 {
			found = append(found, networkInterface)
		}
	}
	return found
}

func (tv *announcer) answerSearch(packet []byte, sender *net.UDPAddr) {
	request, err := http.ReadRequest(bufio.NewReader(bytes.NewReader(packet)))
	if err != nil || request.Method != "M-SEARCH" {
		return
	}
	target := request.Header.Get("ST")
	for _, answered := range matchingTargets(target) {
		tv.reply(sender, answered)
	}
}

// matchingTargets is what to answer a search for target with: everything for ssdp:all.
func matchingTargets(target string) []string {
	if target == "ssdp:all" {
		return searchTargets
	}
	for _, known := range searchTargets {
		if strings.EqualFold(known, target) {
			return []string{known}
		}
	}
	return nil
}

func (tv *announcer) reply(sender *net.UDPAddr, target string) {
	connection, err := net.DialUDP("udp4", nil, sender)
	if err != nil {
		return
	}
	defer connection.Close()
	localAddress := connection.LocalAddr().(*net.UDPAddr).IP
	message := "HTTP/1.1 200 OK\r\n" +
		"CACHE-CONTROL: max-age=1800\r\n" +
		"EXT:\r\n" +
		"LOCATION: " + tv.location(localAddress) + "\r\n" +
		"SERVER: Linux/1.0 UPnP/1.0 OtterVirtualTV/1.0\r\n" +
		"ST: " + target + "\r\n" +
		"USN: " + tv.usn(target) + "\r\n\r\n"
	connection.Write([]byte(message))
	log.Printf("SSDP: answered %s for %s", sender.IP, target)
}

// notifyAlive announces the TV on every interface, then again every notifyEvery.
func (tv *announcer) notifyAlive() {
	for {
		for _, address := range localAddresses() {
			tv.notifyFrom(address)
		}
		time.Sleep(notifyEvery)
	}
}

func (tv *announcer) notifyFrom(address net.IP) {
	connection, err := net.DialUDP("udp4", &net.UDPAddr{IP: address}, ssdpGroup)
	if err != nil {
		return
	}
	defer connection.Close()
	for _, target := range searchTargets {
		message := "NOTIFY * HTTP/1.1\r\n" +
			"HOST: 239.255.255.250:1900\r\n" +
			"CACHE-CONTROL: max-age=1800\r\n" +
			"LOCATION: " + tv.location(address) + "\r\n" +
			"NT: " + target + "\r\n" +
			"NTS: ssdp:alive\r\n" +
			"SERVER: Linux/1.0 UPnP/1.0 OtterVirtualTV/1.0\r\n" +
			"USN: " + tv.usn(target) + "\r\n\r\n"
		connection.Write([]byte(message))
	}
}

func (tv *announcer) location(address net.IP) string {
	return fmt.Sprintf("http://%s:%d/description.xml", address, tv.httpPort)
}

func (tv *announcer) usn(target string) string {
	return "uuid:" + tv.deviceID + "::" + target
}

// localAddresses lists the machine's IPv4 addresses that a phone on the LAN could reach.
func localAddresses() []net.IP {
	addresses := []net.IP{}
	interfaceAddresses, err := net.InterfaceAddrs()
	if err != nil {
		return addresses
	}
	for _, interfaceAddress := range interfaceAddresses {
		network, isNetwork := interfaceAddress.(*net.IPNet)
		if !isNetwork || network.IP.To4() == nil || network.IP.IsLoopback() {
			continue
		}
		addresses = append(addresses, network.IP.To4())
	}
	return addresses
}
