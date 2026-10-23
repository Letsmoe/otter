package tmdb

import (
	"context"
	"fmt"
)

// PersonDetail is the TMDB person record with images and movie credits.
type PersonDetail struct {
	ID                 int     `json:"id"`
	IMDBID             string  `json:"imdb_id"`
	Name               string  `json:"name"`
	Biography          string  `json:"biography"`
	Birthday           string  `json:"birthday"`
	Deathday           string  `json:"deathday"`
	Gender             int     `json:"gender"`
	KnownForDepartment string  `json:"known_for_department"`
	PlaceOfBirth       string  `json:"place_of_birth"`
	Popularity         float64 `json:"popularity"`
	ProfilePath        string  `json:"profile_path"`
	Homepage           string  `json:"homepage"`
	Adult              bool    `json:"adult"`

	Images struct {
		Profiles []Image `json:"profiles"`
	} `json:"images"`
	// Crew carries crew credits (a director's films), Cast the acting roles.
	MovieCredits struct {
		Cast []PersonMovieCredit `json:"cast"`
		Crew []PersonMovieCredit `json:"crew"`
	} `json:"movie_credits"`
}

type PersonMovieCredit struct {
	ID          int     `json:"id"`
	Title       string  `json:"title"`
	Character   string  `json:"character"`
	ReleaseDate string  `json:"release_date"`
	PosterPath  string  `json:"poster_path"`
	Popularity  float64 `json:"popularity"`
	VoteAverage float64 `json:"vote_average"`
	VoteCount   int     `json:"vote_count"`
	Adult       bool    `json:"adult"`
	GenreIDs    []int   `json:"genre_ids"`
	Order       int     `json:"order"`      // cast billing order
	Job         string  `json:"job"`        // crew role (Director, Writer, …)
	Department  string  `json:"department"` // crew department
}

// FetchPersonDetail loads a person with profile images and movie credits.
// Biographies are often untranslated — fall back to English when the
// configured language has none.
func (c *Client) FetchPersonDetail(ctx context.Context, tmdbID int) (*PersonDetail, error) {
	var detail PersonDetail
	params := map[string]string{
		"append_to_response":     "images,movie_credits",
		"include_image_language": "en,null",
	}
	if err := c.get(ctx, fmt.Sprintf("/person/%d", tmdbID), params, &detail); err != nil {
		return nil, err
	}
	if detail.Biography == "" {
		var english struct {
			Biography string `json:"biography"`
		}
		fallbackParams := map[string]string{"language": "en-US"}
		if err := c.get(ctx, fmt.Sprintf("/person/%d", tmdbID), fallbackParams, &english); err == nil {
			detail.Biography = english.Biography
		}
	}
	return &detail, nil
}
