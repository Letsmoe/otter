package main

import (
	"fmt"
	"html"
	"io"
	"log"
	"net/http"
	"regexp"
	"strconv"
	"strings"
)

// control answers the UPnP actions Otter sends a TV and plays them out on the player.
type control struct {
	tv *player
}

// soapCall is one action: its name and the text of its arguments.
type soapCall struct {
	serviceType string
	action      string
	body        string
}

func (handler *control) serve(serviceType string) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(io.LimitReader(request.Body, 1<<20))
		call := soapCall{serviceType: serviceType, action: actionOf(request.Header.Get("SOAPACTION")), body: string(body)}
		answer, err := handler.answer(call)
		if err != nil {
			log.Printf("%s failed: %v", call.action, err)
			writeSOAPFault(writer, err)
			return
		}
		writeSOAPAnswer(writer, call, answer)
	}
}

// actionOf takes "urn:…:AVTransport:1#Play" to "Play".
func actionOf(soapAction string) string {
	trimmed := strings.Trim(soapAction, `"`)
	index := strings.LastIndex(trimmed, "#")
	return trimmed[index+1:]
}

// answer runs the action and returns the answer's argument elements.
func (handler *control) answer(call soapCall) (string, error) {
	tv := handler.tv
	switch call.action {
	case "SetAVTransportURI":
		uri := argument(call.body, "CurrentURI")
		log.Printf("SetAVTransportURI %s", uri)
		tv.setURI(uri)
		return "", nil
	case "Play":
		log.Printf("Play")
		return "", tv.play()
	case "Pause":
		log.Printf("Pause")
		return "", tv.pause()
	case "Stop":
		log.Printf("Stop")
		return "", tv.stop()
	case "Seek":
		target := argument(call.body, "Target")
		log.Printf("Seek %s", target)
		return "", tv.seek(parseClock(target))
	case "GetTransportInfo":
		return "<CurrentTransportState>" + tv.transportState() + "</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus><CurrentSpeed>1</CurrentSpeed>", nil
	case "GetPositionInfo":
		return handler.positionInfo(), nil
	case "GetMediaInfo":
		return "<NrTracks>1</NrTracks><CurrentURI>" + html.EscapeString(tv.currentURI()) + "</CurrentURI>", nil
	case "GetProtocolInfo":
		return "<Source></Source><Sink>" + sinkProtocolInfo + "</Sink>", nil
	case "GetVolume":
		return "<CurrentVolume>" + strconv.Itoa(tv.currentVolume()) + "</CurrentVolume>", nil
	case "SetVolume":
		volume, _ := strconv.Atoi(argument(call.body, "DesiredVolume"))
		log.Printf("SetVolume %d", volume)
		return "", tv.setVolume(volume)
	}
	log.Printf("%s: not handled, answered empty", call.action)
	return "", nil
}

func (handler *control) positionInfo() string {
	position := formatClock(handler.tv.seconds("time-pos"))
	duration := formatClock(handler.tv.seconds("duration"))
	return "<Track>1</Track><TrackDuration>" + duration + "</TrackDuration><TrackURI>" + html.EscapeString(handler.tv.currentURI()) +
		"</TrackURI><RelTime>" + position + "</RelTime><AbsTime>" + position + "</AbsTime><RelCount>0</RelCount><AbsCount>0</AbsCount>"
}

// argument reads one argument's text out of a SOAP body, XML escapes undone.
func argument(body string, name string) string {
	pattern := regexp.MustCompile(`<` + name + `[^>]*>([\s\S]*?)</` + name + `>`)
	match := pattern.FindStringSubmatch(body)
	if match == nil {
		return ""
	}
	return html.UnescapeString(match[1])
}

func writeSOAPAnswer(writer http.ResponseWriter, call soapCall, arguments string) {
	writer.Header().Set("Content-Type", `text/xml; charset="utf-8"`)
	fmt.Fprintf(writer, `<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:%sResponse xmlns:u="%s">%s</u:%sResponse></s:Body></s:Envelope>`,
		call.action, call.serviceType, arguments, call.action)
}

// writeSOAPFault answers with UPnP error 501, "action failed", the way a TV refuses.
func writeSOAPFault(writer http.ResponseWriter, err error) {
	writer.Header().Set("Content-Type", `text/xml; charset="utf-8"`)
	writer.WriteHeader(http.StatusInternalServerError)
	fmt.Fprintf(writer, `<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>501</errorCode><errorDescription>%s</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>`,
		html.EscapeString(err.Error()))
}

// parseClock reads "H:MM:SS" or "H:MM:SS.mmm" as seconds.
func parseClock(clock string) float64 {
	seconds := 0.0
	for _, part := range strings.Split(clock, ":") {
		value, _ := strconv.ParseFloat(part, 64)
		seconds = seconds*60 + value
	}
	return seconds
}

func formatClock(seconds float64) string {
	whole := int(seconds)
	return fmt.Sprintf("%d:%02d:%02d", whole/3600, whole/60%60, whole%60)
}
