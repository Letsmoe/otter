package graphql

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
)

// Ids are "<kind>:<number>" strings: "movie:603", "episode:62085",
// "person:500", "profile:1", "stream:42".
const (
	kindPerson       = "person"
	kindProfile      = "profile"
	kindStream       = "stream"
	kindNotification = "notification"
	kindDownload     = "download"
)

func formatID(kind string, number int) string {
	return kind + ":" + strconv.Itoa(number)
}

func mediaID(ref library.MediaRef) string {
	return formatID(string(ref.MediaType), ref.MediaID)
}

// parseID splits an id and checks its kind.
func parseID(id string, allowedKinds ...string) (string, int, error) {
	kind, number, found := strings.Cut(id, ":")
	if !found {
		return "", 0, fmt.Errorf("invalid id %q", id)
	}
	value, err := strconv.Atoi(number)
	if err != nil {
		return "", 0, fmt.Errorf("invalid id %q", id)
	}
	for _, allowed := range allowedKinds {
		if kind == allowed {
			return kind, value, nil
		}
	}
	return "", 0, fmt.Errorf("id %q is not a %s", id, strings.Join(allowedKinds, " or "))
}

// parseMedia parses a movie, series or episode id.
func parseMedia(id string, allowedTypes ...library.MediaType) (library.MediaRef, error) {
	if len(allowedTypes) == 0 {
		allowedTypes = []library.MediaType{library.MediaTypeMovie, library.MediaTypeSeries, library.MediaTypeEpisode}
	}
	kinds := make([]string, 0, len(allowedTypes))
	for _, mediaType := range allowedTypes {
		kinds = append(kinds, string(mediaType))
	}
	kind, number, err := parseID(id, kinds...)
	if err != nil {
		return library.MediaRef{}, err
	}
	return library.MediaRef{MediaType: library.MediaType(kind), MediaID: number}, nil
}

func parseProfile(id string) (int, error) {
	_, number, err := parseID(id, kindProfile)
	return number, err
}

func parsePerson(id string) (int, error) {
	_, number, err := parseID(id, kindPerson)
	return number, err
}

func parseDownload(id string) (int, error) {
	_, number, err := parseID(id, kindDownload)
	return number, err
}

func parseStream(id string) (int, error) {
	_, number, err := parseID(id, kindStream)
	return number, err
}

func libraryMediaType(mediaType model.MediaType) library.MediaType {
	return library.MediaType(strings.ToLower(string(mediaType)))
}

func modelMediaType(mediaType library.MediaType) model.MediaType {
	return model.MediaType(strings.ToUpper(string(mediaType)))
}

// optionalMediaType maps an optional filter; nil means every type.
func optionalMediaType(mediaType *model.MediaType) library.MediaType {
	if mediaType == nil {
		return ""
	}
	return libraryMediaType(*mediaType)
}

func libraryCatalogTheme(theme model.CatalogTheme) string {
	switch theme {
	case model.CatalogThemeBlockbusters:
		return library.CatalogThemeBlockbusters
	case model.CatalogThemeFeelGood:
		return library.CatalogThemeFeelGood
	case model.CatalogThemeBingeworthy:
		return library.CatalogThemeBingeworthy
	}
	return library.CatalogThemeHiddenGems
}
