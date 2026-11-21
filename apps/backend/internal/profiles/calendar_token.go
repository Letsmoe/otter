package profiles

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"

	"github.com/Letsmoe/otter/internal/db"
)

// calendarTokenBytes is the size of the secret in a calendar feed URL (128 bits).
const calendarTokenBytes = 16

// CalendarToken is the secret that opens the profile's calendar feed. The first
// call makes one; it stays the same until ResetCalendarToken.
func (s *Service) CalendarToken(ctx context.Context, profileID int) (string, error) {
	if _, err := s.Get(ctx, profileID); err != nil {
		return "", err
	}
	row, err := s.database.QueryRow(ctx, "SELECT calendar_token FROM profile WHERE id = $id", db.Vars{"id": profileID})
	if err != nil {
		return "", err
	}
	if token, _ := row["calendar_token"].(string); token != "" {
		return token, nil
	}
	return s.ResetCalendarToken(ctx, profileID)
}

// ResetCalendarToken makes a new secret; the old feed URL stops working.
func (s *Service) ResetCalendarToken(ctx context.Context, profileID int) (string, error) {
	if _, err := s.Get(ctx, profileID); err != nil {
		return "", err
	}
	token, err := newCalendarToken()
	if err != nil {
		return "", err
	}
	_, err = s.database.Exec(ctx, "UPDATE profile SET calendar_token = $token WHERE id = $id",
		db.Vars{"id": profileID, "token": token})
	return token, err
}

// ProfileOfCalendarToken finds the profile a feed URL's secret belongs to; ErrProfileNotFound when none does.
func (s *Service) ProfileOfCalendarToken(ctx context.Context, token string) (*Profile, error) {
	if token == "" {
		return nil, ErrProfileNotFound
	}
	profile, err := db.QueryOneAs[Profile](ctx, s.database, "SELECT * FROM profile WHERE calendar_token = $token",
		db.Vars{"token": token})
	if err != nil {
		return nil, err
	}
	if profile == nil {
		return nil, ErrProfileNotFound
	}
	return profile, nil
}

func newCalendarToken() (string, error) {
	random := make([]byte, calendarTokenBytes)
	if _, err := rand.Read(random); err != nil {
		return "", fmt.Errorf("calendar token: %w", err)
	}
	return hex.EncodeToString(random), nil
}
