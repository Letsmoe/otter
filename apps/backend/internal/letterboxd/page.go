package letterboxd

import (
	"context"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

// maxPageBytes bounds a film page read; the TMDB id sits near the top.
const maxPageBytes = 2 << 20

var (
	pageTMDBID   = regexp.MustCompile(`data-tmdb-id="(\d+)"`)
	pageTMDBType = regexp.MustCompile(`data-tmdb-type="(\w+)"`)
)

// TMDBIDOfFilm reads the TMDB id off a film's Letterboxd page, for films
// whose title and year found no match. uri is the export's address
// (https://boxd.it/..., which redirects to the film page). ok is false when
// the page names no movie id or could not be read.
func TMDBIDOfFilm(ctx context.Context, client *http.Client, uri string) (int, bool) {
	if !isLetterboxdAddress(uri) {
		return 0, false
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, uri, nil)
	if err != nil {
		return 0, false
	}
	response, err := client.Do(request)
	if err != nil {
		return 0, false
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return 0, false
	}
	page, err := io.ReadAll(io.LimitReader(response.Body, maxPageBytes))
	if err != nil {
		return 0, false
	}
	return tmdbMovieIDIn(string(page))
}

// isLetterboxdAddress keeps the lookup to Letterboxd's own hosts.
func isLetterboxdAddress(uri string) bool {
	parsed, err := url.Parse(uri)
	if err != nil || parsed.Scheme != "https" {
		return false
	}
	host := strings.ToLower(parsed.Hostname())
	return host == "boxd.it" || host == "letterboxd.com" || strings.HasSuffix(host, ".letterboxd.com")
}

func tmdbMovieIDIn(page string) (int, bool) {
	id := pageTMDBID.FindStringSubmatch(page)
	if id == nil {
		return 0, false
	}
	kind := pageTMDBType.FindStringSubmatch(page)
	if kind != nil && kind[1] != "movie" {
		return 0, false
	}
	number, err := strconv.Atoi(id[1])
	return number, err == nil && number > 0
}
