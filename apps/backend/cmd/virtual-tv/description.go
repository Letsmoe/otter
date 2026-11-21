package main

import (
	"fmt"
	"net/http"
)

const (
	avTransportType       = "urn:schemas-upnp-org:service:AVTransport:1"
	renderingControlType  = "urn:schemas-upnp-org:service:RenderingControl:1"
	connectionManagerType = "urn:schemas-upnp-org:service:ConnectionManager:1"
)

// What the virtual TV says it plays: the formats Otter sends TVs.
var sinkProtocolInfo = "http-get:*:video/mpeg:*," +
	"http-get:*:video/mp2t:*," +
	"http-get:*:video/vnd.dlna.mpeg-tts:*," +
	"http-get:*:video/mp4:*," +
	"http-get:*:video/x-matroska:*," +
	"http-get:*:application/vnd.apple.mpegurl:*," +
	"http-get:*:audio/mpeg:*," +
	"http-get:*:audio/mp4:*"

func serveDescription(name string, deviceID string) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Content-Type", `text/xml; charset="utf-8"`)
		fmt.Fprintf(writer, deviceDescription, name, deviceID)
	}
}

const deviceDescription = `<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>%s</friendlyName>
    <manufacturer>Otter</manufacturer>
    <modelName>Virtual TV</modelName>
    <UDN>uuid:%s</UDN>
    <serviceList>
      <service>
        <serviceType>` + avTransportType + `</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <SCPDURL>/scpd/AVTransport.xml</SCPDURL>
        <controlURL>/control/AVTransport</controlURL>
        <eventSubURL>/event/AVTransport</eventSubURL>
      </service>
      <service>
        <serviceType>` + renderingControlType + `</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <SCPDURL>/scpd/RenderingControl.xml</SCPDURL>
        <controlURL>/control/RenderingControl</controlURL>
        <eventSubURL>/event/RenderingControl</eventSubURL>
      </service>
      <service>
        <serviceType>` + connectionManagerType + `</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/scpd/ConnectionManager.xml</SCPDURL>
        <controlURL>/control/ConnectionManager</controlURL>
        <eventSubURL>/event/ConnectionManager</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>`

// serveEmptySCPD answers service descriptions with an empty action list: Otter never reads them.
func serveEmptySCPD(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Content-Type", `text/xml; charset="utf-8"`)
	fmt.Fprint(writer, `<?xml version="1.0"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><actionList/><serviceStateTable/></scpd>`)
}
