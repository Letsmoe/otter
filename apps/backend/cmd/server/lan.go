package main

import (
	"net"
	"net/http"
	"regexp"
	"strings"

	"github.com/Letsmoe/otter/internal/subtitles"
)

// DLNA flags: streaming transfer mode, background transfer, connection stalling.
const dlnaFlags = "DLNA.ORG_FLAGS=01700000000000000000000000000000"

var (
	dlnaTypePattern    = regexp.MustCompile(`^video/[A-Za-z0-9.+-]+$`)
	dlnaProfilePattern = regexp.MustCompile(`^[A-Z0-9_]+$`)
)

// dlnaHeaders answers the content-features handshake DLNA TVs (Samsung) make
// before playing. Continuous streams can't serve byte ranges (OP=00); files
// can (OP=01), so the TV seeks in them itself.
func dlnaHeaders(next http.Handler) http.Handler {
	withAnnouncedType := announcedTypeWriter(next)
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("transferMode.dlna.org", "Streaming")
		writer.Header().Set("contentFeatures.dlna.org", contentFeatures(request))
		setCaptionInfo(writer, request)
		withAnnouncedType.ServeHTTP(writer, request)
	})
}

// announcedTypeWriter makes the stream's Content-Type match the type the app
// announced with ?dlnaType= (e.g. video/mp2t instead of video/mpeg).
func announcedTypeWriter(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		contentType := request.URL.Query().Get("dlnaType")
		if !dlnaTypePattern.MatchString(contentType) {
			next.ServeHTTP(writer, request)
			return
		}
		typedWriter := &contentTypeWriter{ResponseWriter: writer, contentType: contentType}
		next.ServeHTTP(typedWriter, request)
		// HEAD handlers may return without writing; the header still has to carry the type.
		if !typedWriter.wroteHeader {
			typedWriter.WriteHeader(http.StatusOK)
		}
	})
}

// contentFeatures names the DLNA profile the app announced with ?dlnaProfile=,
// for TVs (e.g. Hisense VIDAA) that only accept specific profiles.
func contentFeatures(request *http.Request) string {
	operations := "DLNA.ORG_OP=01"
	if strings.HasSuffix(request.URL.Path, "/continuous") {
		operations = "DLNA.ORG_OP=00"
	}
	profile := request.URL.Query().Get("dlnaProfile")
	if dlnaProfilePattern.MatchString(profile) {
		operations = "DLNA.ORG_PN=" + profile + ";" + operations
	}
	return operations + ";DLNA.ORG_CI=0;" + dlnaFlags
}

type contentTypeWriter struct {
	http.ResponseWriter
	contentType string
	wroteHeader bool
}

func (writer *contentTypeWriter) WriteHeader(statusCode int) {
	if !writer.wroteHeader && statusCode < 300 {
		writer.Header().Set("Content-Type", writer.contentType)
	}
	writer.wroteHeader = true
	writer.ResponseWriter.WriteHeader(statusCode)
}

func (writer *contentTypeWriter) Write(data []byte) (int, error) {
	if !writer.wroteHeader {
		writer.WriteHeader(http.StatusOK)
	}
	return writer.ResponseWriter.Write(data)
}

// Flush keeps continuous streams flowing through the wrapper.
func (writer *contentTypeWriter) Flush() {
	if flusher, ok := writer.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (writer *contentTypeWriter) Unwrap() http.ResponseWriter {
	return writer.ResponseWriter
}

var subtitleIDPattern = regexp.MustCompile(`^[0-9a-f]{32}$`)

// setCaptionInfo points Samsung TVs at the SRT file of the subtitle the app
// asked for with ?subtitle=<id>; the TV loads it next to the video.
func setCaptionInfo(writer http.ResponseWriter, request *http.Request) {
	subtitleID := request.URL.Query().Get("subtitle")
	if !subtitleIDPattern.MatchString(subtitleID) {
		return
	}
	captionURL := "http://" + request.Host + subtitles.RoutePrefix + subtitleID + ".srt"
	writer.Header().Set("CaptionInfo.sec", captionURL)
}

// privateNetworkOnly refuses clients outside loopback and private ranges, so
// the LAN stream port never serves the internet (e.g. over mobile data).
func privateNetworkOnly(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if !isPrivateClient(request.RemoteAddr) {
			http.Error(writer, "forbidden", http.StatusForbidden)
			return
		}
		next.ServeHTTP(writer, request)
	})
}

func isPrivateClient(remoteAddress string) bool {
	host, _, err := net.SplitHostPort(remoteAddress)
	if err != nil {
		return false
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return false
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast()
}
