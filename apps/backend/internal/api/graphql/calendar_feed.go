package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/calendar"
)

// calendarFeedPath is the path of the profile's calendar feed; reset first makes a new secret.
func (r *Resolver) calendarFeedPath(ctx context.Context, profileID string, reset bool) (string, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return "", err
	}
	var token string
	if reset {
		token, err = r.Profiles.ResetCalendarToken(ctx, profile)
	} else {
		token, err = r.Profiles.CalendarToken(ctx, profile)
	}
	if err != nil {
		return "", err
	}
	return calendar.RoutePrefix + token + calendar.FileSuffix, nil
}
