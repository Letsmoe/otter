package letterboxd

import (
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const (
	feedURLFormat = "https://letterboxd.com/%s/rss/"
	// A diary feed lists the latest 50 or so entries; this is plenty.
	maxFeedBytes = 8 << 20
)

// The extra fields of a Letterboxd feed sit in the namespaces
// https://letterboxd.com and https://themoviedb.org, named in the tags below.
type feedDocument struct {
	Items []feedItem `xml:"channel>item"`
}

type feedItem struct {
	Title       string `xml:"title"`
	PubDate     string `xml:"pubDate"`
	WatchedDate string `xml:"https://letterboxd.com watchedDate"`
	Rewatch     string `xml:"https://letterboxd.com rewatch"`
	FilmTitle   string `xml:"https://letterboxd.com filmTitle"`
	FilmYear    string `xml:"https://letterboxd.com filmYear"`
	Rating      string `xml:"https://letterboxd.com memberRating"`
	TMDBMovieID string `xml:"https://themoviedb.org movieId"`
}

// FeedItem is one film logged in a public diary feed.
type FeedItem struct {
	Film
	// TMDBID is the film's TMDB id, which the feed carries.
	TMDBID int
	// Date is the day it was watched (YYYY-MM-DD), else the day it was logged.
	Date string
	// Stars is 0.5 to 5; 0 when the entry carries no rating.
	Stars   float64
	Rewatch bool
}

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_-]{1,40}$`)

// NormalizeUsername accepts a username or the address of a profile page.
func NormalizeUsername(input string) (string, error) {
	name := strings.TrimSpace(input)
	if strings.Contains(name, "/") {
		parsed, err := url.Parse(name)
		if err != nil || !strings.HasSuffix(strings.ToLower(parsed.Hostname()), "letterboxd.com") {
			return "", errors.New("that is not a Letterboxd profile address")
		}
		name = strings.Trim(parsed.Path, "/")
		name, _, _ = strings.Cut(name, "/")
	}
	name = strings.TrimPrefix(name, "@")
	if !usernamePattern.MatchString(name) {
		return "", errors.New("a Letterboxd username has only letters, digits and underscores")
	}
	return name, nil
}

// ParseFeed reads a diary feed's films; entries without a TMDB id (lists,
// reviews of lists) are skipped.
func ParseFeed(content []byte) ([]FeedItem, error) {
	var document feedDocument
	if err := xml.Unmarshal(content, &document); err != nil {
		return nil, fmt.Errorf("the feed is not valid XML: %w", err)
	}
	var items []FeedItem
	for _, raw := range document.Items {
		item, ok := itemOf(raw)
		if ok {
			items = append(items, item)
		}
	}
	return items, nil
}

func itemOf(raw feedItem) (FeedItem, bool) {
	tmdbID, _ := strconv.Atoi(strings.TrimSpace(raw.TMDBMovieID))
	if tmdbID == 0 {
		return FeedItem{}, false
	}
	year, _ := strconv.Atoi(strings.TrimSpace(raw.FilmYear))
	stars, _ := strconv.ParseFloat(strings.TrimSpace(raw.Rating), 64)
	return FeedItem{
		Film:    Film{Name: strings.TrimSpace(raw.FilmTitle), Year: year},
		TMDBID:  tmdbID,
		Date:    feedDate(raw),
		Stars:   validStars(stars),
		Rewatch: strings.EqualFold(strings.TrimSpace(raw.Rewatch), "yes"),
	}, true
}

// feedDate prefers the day of watching; an entry without it was logged
// the day it was published.
func feedDate(raw feedItem) string {
	watched := strings.TrimSpace(raw.WatchedDate)
	if _, err := time.Parse("2006-01-02", watched); err == nil {
		return watched
	}
	published, err := time.Parse(time.RFC1123Z, strings.TrimSpace(raw.PubDate))
	if err != nil {
		return ""
	}
	return published.UTC().Format("2006-01-02")
}

// ErrFeedNotFound is returned for a username Letterboxd does not know, or
// whose diary is private.
var ErrFeedNotFound = errors.New("Letterboxd has no public diary for that username")

// FetchFeed downloads and reads a member's public diary feed.
func FetchFeed(ctx context.Context, client *http.Client, username string) ([]FeedItem, error) {
	return fetchFeedFrom(ctx, client, fmt.Sprintf(feedURLFormat, username))
}

func fetchFeedFrom(ctx context.Context, client *http.Client, address string) ([]FeedItem, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.5")
	response, err := client.Do(request)
	if err != nil {
		return nil, fmt.Errorf("could not reach Letterboxd: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotFound {
		return nil, ErrFeedNotFound
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Letterboxd answered HTTP %d", response.StatusCode)
	}
	content, err := io.ReadAll(io.LimitReader(response.Body, maxFeedBytes))
	if err != nil {
		return nil, err
	}
	return ParseFeed(content)
}
