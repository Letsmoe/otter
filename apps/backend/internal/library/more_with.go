package library

import (
	"context"
	"slices"

	"github.com/neoworks/otter/internal/db"
)

// WellRatedMinimum is the lowest 1-10 rating that counts as liking a title
// (the "Good" bucket of the rating buttons is 7.5).
const WellRatedMinimum = 7

// MoreWith is a row of titles featuring the lead actor of a title the
// profile liked.
type MoreWith struct {
	Person    Person
	BecauseOf Title
	Titles    []Title
}

type likedLead struct {
	PersonID int `json:"person_id"`
	MediaID  int `json:"media_id"`
}

// MoreWithLead finds the lead actor of the profile's most recently well-rated
// title of the given type and lists their other titles of that type. Nil when
// the profile hasn't rated anything well yet.
func (s *Service) MoreWithLead(ctx context.Context, profileID int, mediaType MediaType) (*MoreWith, error) {
	lead, err := db.QueryOneAs[likedLead](ctx, s.Store.database, `SELECT credit.person_id, rating.media_id
		FROM user_rating AS rating
		JOIN credit ON credit.media_type = rating.media_type AND credit.media_id = rating.media_id
			AND credit.role = 'cast'
		WHERE rating.profile_id = $profile_id AND rating.media_type = $media_type
			AND rating.rating >= $minimum
		ORDER BY rating.rated_at DESC, credit.credit_order ASC
		LIMIT 1`,
		db.Vars{"profile_id": profileID, "media_type": string(mediaType), "minimum": WellRatedMinimum})
	if err != nil || lead == nil {
		return nil, err
	}
	person, err := s.EnsurePerson(ctx, lead.PersonID)
	if err != nil {
		return nil, err
	}
	source := MediaRef{MediaType: mediaType, MediaID: lead.MediaID}
	becauseOf, err := s.Store.Titles(ctx, []MediaRef{source})
	if err != nil || len(becauseOf) == 0 {
		return nil, err
	}
	titles, err := s.Store.Filmography(ctx, lead.PersonID, "")
	if err != nil {
		return nil, err
	}
	titles = slices.DeleteFunc(titles, func(title Title) bool {
		return title.MediaType != mediaType || title.TMDBID == lead.MediaID || title.Adult
	})
	return &MoreWith{Person: *person, BecauseOf: becauseOf[0], Titles: titles}, nil
}
