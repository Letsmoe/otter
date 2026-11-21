// Command virtual-tv is a DLNA TV on this computer, for testing Otter's casting without walking
// to the TV: it shows up in the app's TV list, plays what the app sends in an mpv window and logs
// every command it gets.
//
//	go run ./cmd/virtual-tv [-name "Virtual TV"] [-port 49494] [-mpv mpv]
package main

import (
	"crypto/sha1"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
)

func main() {
	name := flag.String("name", "Virtual TV", "the name the app lists the TV under")
	port := flag.Int("port", 49494, "the HTTP port for the description and the commands")
	mpvPath := flag.String("mpv", "mpv", "the mpv binary that shows the picture")
	flag.Parse()

	tv, err := startPlayer(*mpvPath, *name)
	if err != nil {
		log.Fatal(err)
	}
	deviceID := stableDeviceID(*name)
	handler := &control{tv: tv}
	routes := http.NewServeMux()
	routes.HandleFunc("/description.xml", serveDescription(*name, deviceID))
	routes.HandleFunc("/scpd/", serveEmptySCPD)
	routes.HandleFunc("/control/AVTransport", handler.serve(avTransportType))
	routes.HandleFunc("/control/RenderingControl", handler.serve(renderingControlType))
	routes.HandleFunc("/control/ConnectionManager", handler.serve(connectionManagerType))

	go func() {
		announcer := &announcer{deviceID: deviceID, httpPort: *port}
		log.Fatal(announcer.run())
	}()
	log.Printf("%s is on at port %d (%v)", *name, *port, localAddresses())
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%d", *port), routes))
}

// stableDeviceID keeps the TV's UDN across restarts, so the app recognises it as the same TV.
func stableDeviceID(name string) string {
	host, _ := os.Hostname()
	sum := sha1.Sum([]byte(host + "/" + name))
	return fmt.Sprintf("%x-%x-%x-%x-%x", sum[0:4], sum[4:6], sum[6:8], sum[8:10], sum[10:16])
}
