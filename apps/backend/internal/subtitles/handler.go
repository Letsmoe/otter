package subtitles

import (
	"bytes"
	"errors"
	"log"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// RoutePrefix is where Handler is mounted.
const RoutePrefix = "/api/subtitles/"

// maxOffsetMillis bounds ?offset so a typo cannot push every cue out of range.
const maxOffsetMillis = 60 * 60 * 1000

var fileName = regexp.MustCompile(`^([0-9a-f]{32})\.(vtt|srt)$`)

// Handler serves GET /api/subtitles/<id>.vtt and <id>.srt: the cleaned file
// as UTF-8, shifted by the optional ?offset=<ms>.
func (s *Service) Handler() http.Handler {
	return http.HandlerFunc(s.serve)
}

func (s *Service) serve(writer http.ResponseWriter, request *http.Request) {
	groups := fileName.FindStringSubmatch(strings.TrimPrefix(request.URL.Path, RoutePrefix))
	if groups == nil {
		http.NotFound(writer, request)
		return
	}
	offsetMillis, err := parseOffset(request.URL.Query().Get("offset"))
	if err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return
	}
	cues, err := s.Cues(request.Context(), groups[1])
	if err != nil {
		writeFileError(writer, groups[1], err)
		return
	}
	body, contentType := render(ShiftCues(cues, offsetMillis), groups[2])
	writer.Header().Set("Content-Type", contentType)
	writer.Header().Set("Cache-Control", "public, max-age=3600")
	http.ServeContent(writer, request, "", time.Time{}, bytes.NewReader([]byte(body)))
}

func render(cues []Cue, extension string) (body, contentType string) {
	if extension == "srt" {
		return RenderSRT(cues), "application/x-subrip; charset=utf-8"
	}
	return RenderVTT(cues), "text/vtt; charset=utf-8"
}

func parseOffset(raw string) (int, error) {
	if raw == "" {
		return 0, nil
	}
	offsetMillis, err := strconv.Atoi(raw)
	if err != nil || offsetMillis > maxOffsetMillis || offsetMillis < -maxOffsetMillis {
		return 0, errors.New("offset must be milliseconds within an hour")
	}
	return offsetMillis, nil
}

func writeFileError(writer http.ResponseWriter, id string, err error) {
	if errors.Is(err, ErrNotFound) {
		http.Error(writer, "subtitle not found", http.StatusNotFound)
		return
	}
	log.Printf("subtitles: serve %s: %v", id, err)
	http.Error(writer, "subtitle unavailable", http.StatusBadGateway)
}
