package notify

import (
	"fmt"
	"strconv"
	"time"

	"github.com/neoworks/otter/internal/library"
)

const (
	dateLayout = "2006-01-02"
	// Episodes aired longer ago than this are not news any more.
	newEpisodeWindow = 7 * 24 * time.Hour
	// Releases further out than this are not announced yet.
	comingSoonWindow = 30 * 24 * time.Hour
)

// Notice is a notification before it is addressed to a profile. Date is the
// day the event happens (release or air date); profiles that saved the title
// after an aired episode's date are not told about it.
type Notice struct {
	Kind      Kind
	Ref       library.MediaRef
	EpisodeID int
	Heading   string
	Body      string
	Date      string
}

func parseDate(value string) (time.Time, bool) {
	parsed, err := time.Parse(dateLayout, value)
	if err != nil {
		return time.Time{}, false
	}
	return parsed, true
}

// comingHeading reads like Netflix: "Coming 11 February".
func comingHeading(date time.Time) string {
	return "Coming " + strconv.Itoa(date.Day()) + " " + date.Month().String()
}

func episodeLine(episode library.Episode) string {
	code := fmt.Sprintf("S%d:E%d", episode.SeasonNumber, episode.EpisodeNumber)
	if episode.Name == "" {
		return code
	}
	return code + " · " + episode.Name
}

func sourcesLine(count int) string {
	if count == 1 {
		return "1 source found"
	}
	return strconv.Itoa(count) + " sources found"
}

func isUpcoming(date time.Time, today time.Time) bool {
	return date.After(today) && date.Sub(today) <= comingSoonWindow
}

// MovieNotices decides what a watched movie is worth telling: an upcoming
// release, or links found by a lookup the watcher itself ran (hadStreams
// false, foundCount > 0).
func MovieNotices(movie library.Title, hadStreams bool, foundCount int, today time.Time) []Notice {
	release, known := parseDate(movie.ReleaseDate)
	if known && isUpcoming(release, today) {
		body := "On your list"
		if movie.TrailerKey != "" {
			body = "Play the trailer"
		}
		return []Notice{{Kind: KindComingSoon, Ref: movie.Ref(), Heading: comingHeading(release), Body: body, Date: movie.ReleaseDate}}
	}
	if hadStreams || foundCount == 0 {
		return nil
	}
	return []Notice{{Kind: KindAvailable, Ref: movie.Ref(), Heading: "Now available", Body: sourcesLine(foundCount), Date: today.Format(dateLayout)}}
}

// SeriesNotices announces recently aired episodes and the next upcoming one.
// episodes must be in airing order.
func SeriesNotices(series library.Title, episodes []library.Episode, today time.Time) []Notice {
	notices := []Notice{}
	for _, episode := range episodes {
		airDate, known := parseDate(episode.AirDate)
		if !known || episode.SeasonNumber == 0 {
			continue
		}
		if isUpcoming(airDate, today) {
			notices = append(notices, episodeNotice(KindComingSoon, series, episode, comingHeading(airDate)))
			return notices
		}
		if !airDate.After(today) && today.Sub(airDate) <= newEpisodeWindow {
			notices = append(notices, episodeNotice(KindNewEpisode, series, episode, "New episode"))
		}
	}
	return notices
}

func episodeNotice(kind Kind, series library.Title, episode library.Episode, heading string) Notice {
	return Notice{
		Kind:      kind,
		Ref:       series.Ref(),
		EpisodeID: episode.TMDBID,
		Heading:   heading,
		Body:      episodeLine(episode),
		Date:      episode.AirDate,
	}
}

// AppliesTo reports whether a profile that saved the title at addedAt should
// get the notice: news about episodes aired before it was saved is not news.
func (n Notice) AppliesTo(addedAt string) bool {
	if n.Kind != KindNewEpisode || len(addedAt) < len(dateLayout) {
		return true
	}
	return n.Date >= addedAt[:len(dateLayout)]
}
