// Package library is otter's core loop: fetch a title from TMDB, find its
// streams through the streams plugins, and record what TMDB says is related.
// Everything lands in SQLite, where recommendations are computed from it.
package library

// MediaType discriminates rows that can point at several kinds of title.
type MediaType string

const (
	MediaTypeMovie   MediaType = "movie"
	MediaTypeSeries  MediaType = "series"
	MediaTypeEpisode MediaType = "episode"
)

// Title is the shared shape of movies and series. Columns that only exist
// on one of the two tables stay zero for the other.
type Title struct {
	MediaType     MediaType `json:"media_type"`
	TMDBID        int       `json:"tmdb_id"`
	IMDBID        string    `json:"imdb_id"`
	Title         string    `json:"title"`
	OriginalTitle string    `json:"original_title"`
	Overview      string    `json:"overview"`
	Tagline       string    `json:"tagline"`
	// ReleaseDate is the movie release or series first-air date.
	ReleaseDate     string   `json:"release_date"`
	Runtime         int      `json:"runtime"`
	Status          string   `json:"status"`
	Popularity      float64  `json:"popularity"`
	VoteAverage     float64  `json:"vote_average"`
	VoteCount       int      `json:"vote_count"`
	PosterPath      string   `json:"poster_path"`
	BackdropPath    string   `json:"backdrop_path"`
	LogoPath        string   `json:"logo_path"`
	TrailerKey      string   `json:"trailer_key"`
	Genres          []string `json:"genres"`
	Keywords        []string `json:"keywords"`
	Adult           bool     `json:"adult"`
	DetailFetchedAt string   `json:"detail_fetched_at"`
}

// Ref identifies the title in the library.
func (t Title) Ref() MediaRef {
	return MediaRef{MediaType: t.MediaType, MediaID: t.TMDBID}
}

// Year returns the release year, or 0 when the date is unknown.
func (t Title) Year() int {
	return yearOf(t.ReleaseDate)
}

type Season struct {
	TMDBID          int    `json:"tmdb_id"`
	SeriesID        int    `json:"series_id"`
	SeasonNumber    int    `json:"season_number"`
	Name            string `json:"name"`
	Overview        string `json:"overview"`
	AirDate         string `json:"air_date"`
	PosterPath      string `json:"poster_path"`
	EpisodeCount    int    `json:"episode_count"`
	DetailFetchedAt string `json:"detail_fetched_at"`
}

type Episode struct {
	TMDBID           int     `json:"tmdb_id"`
	SeriesID         int     `json:"series_id"`
	SeasonNumber     int     `json:"season_number"`
	EpisodeNumber    int     `json:"episode_number"`
	Name             string  `json:"name"`
	Overview         string  `json:"overview"`
	AirDate          string  `json:"air_date"`
	Runtime          int     `json:"runtime"`
	StillPath        string  `json:"still_path"`
	VoteAverage      float64 `json:"vote_average"`
	StreamsFetchedAt string  `json:"streams_fetched_at"`
}

type Person struct {
	TMDBID             int     `json:"tmdb_id"`
	IMDBID             string  `json:"imdb_id"`
	Name               string  `json:"name"`
	ProfilePath        string  `json:"profile_path"`
	Biography          string  `json:"biography"`
	Birthday           string  `json:"birthday"`
	Deathday           string  `json:"deathday"`
	PlaceOfBirth       string  `json:"place_of_birth"`
	KnownForDepartment string  `json:"known_for_department"`
	Popularity         float64 `json:"popularity"`
	DetailFetchedAt    string  `json:"detail_fetched_at"`
}

// Credit is a person's role on a title, joined with the person's name and
// picture for display.
type Credit struct {
	MediaType   MediaType `json:"media_type"`
	MediaID     int       `json:"media_id"`
	PersonID    int       `json:"person_id"`
	Role        string    `json:"role"` // cast | crew
	Job         string    `json:"job"`
	Character   string    `json:"character"`
	Order       int       `json:"credit_order"`
	Name        string    `json:"name"`
	ProfilePath string    `json:"profile_path"`
}

const (
	RoleCast = "cast"
	RoleCrew = "crew"
)

type Stream struct {
	ID        int       `json:"id"`
	MediaType MediaType `json:"media_type"`
	MediaID   int       `json:"media_id"`
	Plugin    string    `json:"plugin"`
	URL       string    `json:"url"`
	Label     string    `json:"label"`
	Quality   string    `json:"quality"`
	Language  string    `json:"language"`
	Format    string    `json:"format"`
	SizeBytes int64     `json:"size_bytes"`
	SpeedBps  float64   `json:"speed_bps"`
	// Playable is nil until a resolve was attempted.
	Playable *bool  `json:"playable"`
	FoundAt  string `json:"found_at"`
}

// MediaRef points at one title of any kind.
type MediaRef struct {
	MediaType MediaType `json:"media_type"`
	MediaID   int       `json:"media_id"`
}
