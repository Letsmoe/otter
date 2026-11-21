package tmdb

import (
	"context"
	"fmt"
)

// Certification is the age rating one country gave a title, as TMDB spells it
// (e.g. "12" for Germany's FSK, "PG-13" for the US).
type Certification struct {
	Country string
	Rating  string
}

type releaseDatesResponse struct {
	Results []struct {
		Country      string `json:"iso_3166_1"`
		ReleaseDates []struct {
			Certification string `json:"certification"`
			Type          int    `json:"type"`
		} `json:"release_dates"`
	} `json:"results"`
}

type contentRatingsResponse struct {
	Results []struct {
		Country string `json:"iso_3166_1"`
		Rating  string `json:"rating"`
	} `json:"results"`
}

// theatricalRelease is TMDB's release type 3, whose certification counts most.
const theatricalRelease = 3

// Certifications returns the age ratings countries gave a movie (release
// dates) or series (content ratings). mediaType is "movie" or "series".
func (c *Client) Certifications(ctx context.Context, mediaType string, tmdbID int) ([]Certification, error) {
	if mediaType == "series" {
		var response contentRatingsResponse
		if err := c.get(ctx, fmt.Sprintf("/tv/%d/content_ratings", tmdbID), nil, &response); err != nil {
			return nil, err
		}
		return seriesCertifications(response), nil
	}
	var response releaseDatesResponse
	if err := c.get(ctx, fmt.Sprintf("/movie/%d/release_dates", tmdbID), nil, &response); err != nil {
		return nil, err
	}
	return movieCertifications(response), nil
}

func seriesCertifications(response contentRatingsResponse) []Certification {
	certifications := []Certification{}
	for _, result := range response.Results {
		if result.Rating != "" {
			certifications = append(certifications, Certification{Country: result.Country, Rating: result.Rating})
		}
	}
	return certifications
}

// movieCertifications takes each country's theatrical certification, else the first one it gave.
func movieCertifications(response releaseDatesResponse) []Certification {
	certifications := []Certification{}
	for _, result := range response.Results {
		rating := ""
		for _, release := range result.ReleaseDates {
			if release.Certification == "" {
				continue
			}
			if rating == "" || release.Type == theatricalRelease {
				rating = release.Certification
			}
		}
		if rating != "" {
			certifications = append(certifications, Certification{Country: result.Country, Rating: rating})
		}
	}
	return certifications
}
