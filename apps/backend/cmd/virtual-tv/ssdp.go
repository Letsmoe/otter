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

func (tv *announcer) run() error {
	listener, err := net.ListenMulticastUDP("udp4", nil, ssdpGroup)
	if err != nil {
		return fmt.Errorf("joining SSDP: %w", err)
	}
	go tv.notifyAlive()
	buffer := make([]byte, 4096)
	for {
		count, sender, err := listener.ReadFromUDP(buffer)
		if err != nil {
			return err
		}
		tv.answerSearch(buffer[:count], sender)
	}
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
