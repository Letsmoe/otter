package library

import (
	"context"
	"log"
	"sync"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/tmdb"
)

// How many titles' ratings are fetched from TMDB at once.
const certificationFetchers = 6

// fetchedMarker is the country of the row that says a title's ratings were fetched.
const fetchedMarker = ""

// SaveCertifications stores what countries rate a title, replacing earlier
// ratings, and marks the title as fetched even when nobody rated it.
func (s *Store) SaveCertifications(ctx context.Context, ref MediaRef, certifications []tmdb.Certification) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		vars := db.Vars{"media_type": string(ref.MediaType), "media_id": ref.MediaID, "now": db.Now()}
		if _, err := tx.Exec(ctx, "DELETE FROM title_certification WHERE media_type = $media_type AND media_id = $media_id", vars); err != nil {
			return err
		}
		insert := `INSERT INTO title_certification (media_type, media_id, country, certification, fetched_at)
			VALUES ($media_type, $media_id, $country, $certification, $now)`
		rows := append([]tmdb.Certification{{Country: fetchedMarker}}, certifications...)
		for _, row := range rows {
			vars["country"] = row.Country
			vars["certification"] = row.Rating
			if _, err := tx.Exec(ctx, insert, vars); err != nil {
				return err
			}
		}
		return nil
	})
}

// StoredCertifications is what the library knows about a title's ratings.
type StoredCertifications struct {
	// Fetched is false until TMDB was asked.
	Fetched bool
	// ByCountry maps an ISO 3166-1 country to its certification.
	ByCountry map[string]string
}

// Certifications loads the stored ratings of several titles in one query.
func (s *Store) Certifications(ctx context.Context, refs []MediaRef) (map[MediaRef]StoredCertifications, error) {
	stored := map[MediaRef]StoredCertifications{}
	for _, mediaType := range []MediaType{MediaTypeMovie, MediaTypeSeries} {
		ids := idsOfType(refs, mediaType)
		if len(ids) == 0 {
			continue
		}
		rows, err := s.database.Query(ctx, `SELECT media_id, country, certification FROM title_certification
			WHERE media_type = $media_type AND media_id IN (SELECT value FROM json_each($ids))`,
			db.Vars{"media_type": string(mediaType), "ids": ids})
		if err != nil {
			return nil, err
		}
		for _, row := range rows {
			ref := MediaRef{MediaType: mediaType, MediaID: db.AsInt(row["media_id"])}
			entry := stored[ref]
			if entry.ByCountry == nil {
				entry.ByCountry = map[string]string{}
			}
			entry.Fetched = true
			country, _ := row["country"].(string)
			certification, _ := row["certification"].(string)
			if country != fetchedMarker {
				entry.ByCountry[country] = certification
			}
			stored[ref] = entry
		}
	}
	return stored, nil
}

// AgeLimit is the oldest age a viewer may watch titles rated for, in one country's ratings.
type AgeLimit struct {
	Age     int
	Country string
}

// AllowedForAge says for each title whether a viewer within limit may see it.
// Titles whose ratings were never fetched are fetched first. A title without a
// readable rating in the country, or one TMDB flags as adult, is not allowed.
func (s *Service) AllowedForAge(ctx context.Context, refs []MediaRef, limit AgeLimit) (map[MediaRef]bool, error) {
	stored, err := s.Store.Certifications(ctx, refs)
	if err != nil {
		return nil, err
	}
	s.fetchMissingCertifications(ctx, refs, stored)
	adult, err := s.adultTitles(ctx, refs)
	if err != nil {
		return nil, err
	}
	allowed := make(map[MediaRef]bool, len(refs))
	for _, ref := range refs {
		allowed[ref] = !adult[ref] && withinLimit(stored[ref], limit)
	}
	return allowed, nil
}

func withinLimit(certifications StoredCertifications, limit AgeLimit) bool {
	age, known := MinimumAge(limit.Country, certifications.ByCountry[limit.Country])
	return known && age <= limit.Age
}

func (s *Service) adultTitles(ctx context.Context, refs []MediaRef) (map[MediaRef]bool, error) {
	titles, err := s.Store.Titles(ctx, titleRefs(refs))
	if err != nil {
		return nil, err
	}
	adult := map[MediaRef]bool{}
	for _, title := range titles {
		adult[title.Ref()] = title.Adult
	}
	return adult, nil
}

// titleRefs keeps the movies and series of refs; episodes have no ratings of their own.
func titleRefs(refs []MediaRef) []MediaRef {
	titles := make([]MediaRef, 0, len(refs))
	for _, ref := range refs {
		if ref.MediaType == MediaTypeMovie || ref.MediaType == MediaTypeSeries {
			titles = append(titles, ref)
		}
	}
	return titles
}

// fetchMissingCertifications asks TMDB for the titles never asked about and adds the answers to stored.
func (s *Service) fetchMissingCertifications(ctx context.Context, refs []MediaRef, stored map[MediaRef]StoredCertifications) {
	var missing []MediaRef
	for _, ref := range titleRefs(refs) {
		if !stored[ref].Fetched {
			missing = append(missing, ref)
		}
	}
	var mutex sync.Mutex
	slots := make(chan struct{}, certificationFetchers)
	var group sync.WaitGroup
	for _, ref := range missing {
		group.Add(1)
		slots <- struct{}{}
		go func() {
			defer group.Done()
			defer func() { <-slots }()
			fetched, ok := s.fetchCertifications(ctx, ref)
			if !ok {
				return
			}
			mutex.Lock()
			stored[ref] = fetched
			mutex.Unlock()
		}()
	}
	group.Wait()
}

// fetchCertifications asks TMDB for one title's ratings and stores them; false when TMDB didn't answer.
func (s *Service) fetchCertifications(ctx context.Context, ref MediaRef) (StoredCertifications, bool) {
	certifications, err := s.tmdb.Certifications(ctx, string(ref.MediaType), ref.MediaID)
	if err != nil {
		log.Printf("library: ratings of %s %d: %v", ref.MediaType, ref.MediaID, err)
		return StoredCertifications{}, false
	}
	if err := s.Store.SaveCertifications(ctx, ref, certifications); err != nil {
		log.Printf("library: save ratings of %s %d: %v", ref.MediaType, ref.MediaID, err)
	}
	byCountry := make(map[string]string, len(certifications))
	for _, certification := range certifications {
		byCountry[certification.Country] = certification.Rating
	}
	return StoredCertifications{Fetched: true, ByCountry: byCountry}, true
}
