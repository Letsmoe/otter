package graphql

import (
	"context"
	"errors"
	"net/http"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
	"github.com/Letsmoe/otter/internal/recommend"
)

// profileHeader tells which profile a client acts as ("profile:2"), so lists
// can leave out what its age limit holds back without every query naming it.
const profileHeader = "X-Otter-Profile"

// errNotForProfile is what a title above the profile's age limit answers with.
var errNotForProfile = errors.New("this title is not available for this profile")

type requestProfileKey struct{}

// withRequestProfile makes the profile named by the request's header available to resolvers.
func withRequestProfile(next http.Handler) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		profileID, err := parseProfile(request.Header.Get(profileHeader))
		if err == nil {
			request = request.WithContext(context.WithValue(request.Context(), requestProfileKey{}, profileID))
		}
		next.ServeHTTP(response, request)
	})
}

func requestProfile(ctx context.Context) (int, bool) {
	profileID, ok := ctx.Value(requestProfileKey{}).(int)
	return profileID, ok
}

// ageGate holds titles back from a child profile by their age rating; the zero value holds back nothing.
type ageGate struct {
	active bool
	limit  library.AgeLimit
}

// gateForProfile is the gate of a profile; open when it has no age limit or doesn't exist.
func (r *Resolver) gateForProfile(ctx context.Context, profileID int) (ageGate, error) {
	profile, err := r.Profiles.Get(ctx, profileID)
	if errors.Is(err, profiles.ErrProfileNotFound) {
		return ageGate{}, nil
	}
	if err != nil {
		return ageGate{}, err
	}
	if !profile.HasAgeLimit() {
		return ageGate{}, nil
	}
	_, tmdbLanguage := r.Settings.TMDB()
	country := library.CountryFor(profile.LanguageList(), tmdbLanguage)
	return ageGate{active: true, limit: library.AgeLimit{Age: profile.AgeLimit, Country: country}}, nil
}

// requestGate is the gate of the profile the request's header names.
func (r *Resolver) requestGate(ctx context.Context) (ageGate, error) {
	profileID, ok := requestProfile(ctx)
	if !ok {
		return ageGate{}, nil
	}
	return r.gateForProfile(ctx, profileID)
}

// allowedRefs says which of refs the gate lets through; every one when it is open.
func (r *Resolver) allowedRefs(ctx context.Context, gate ageGate, refs []library.MediaRef) (map[library.MediaRef]bool, error) {
	if !gate.active {
		return nil, nil
	}
	return r.Library.AllowedForAge(ctx, refs, gate.limit)
}

// visibleTitles leaves out the titles the request's profile may not see.
func (r *Resolver) visibleTitles(ctx context.Context, titles []*model.Title) ([]*model.Title, error) {
	gate, err := r.requestGate(ctx)
	if err != nil {
		return nil, err
	}
	return r.gatedTitles(ctx, gate, titles)
}

func (r *Resolver) gatedTitles(ctx context.Context, gate ageGate, titles []*model.Title) ([]*model.Title, error) {
	refs := make([]library.MediaRef, 0, len(titles))
	for _, title := range titles {
		refs = append(refs, titleRef(title))
	}
	allowed, err := r.allowedRefs(ctx, gate, refs)
	if err != nil || allowed == nil {
		return titles, err
	}
	kept := make([]*model.Title, 0, len(titles))
	for _, title := range titles {
		if allowed[titleRef(title)] {
			kept = append(kept, title)
		}
	}
	return kept, nil
}

// countryOfProfile is the country whose ratings apply to a profile.
func (r *Resolver) countryOfProfile(ctx context.Context, profileID int) (string, error) {
	profile, err := r.Profiles.Get(ctx, profileID)
	if err != nil {
		return "", err
	}
	_, tmdbLanguage := r.Settings.TMDB()
	return library.CountryFor(profile.LanguageList(), tmdbLanguage), nil
}

// ageRatingOf is the title's stored rating in the profile's country as shown on screen; nil when unknown.
func (r *Resolver) ageRatingOf(ctx context.Context, profileID int, ref library.MediaRef) (*string, error) {
	country, err := r.countryOfProfile(ctx, profileID)
	if err != nil {
		return nil, err
	}
	stored, err := r.Library.Store.Certifications(ctx, []library.MediaRef{ref})
	if err != nil {
		return nil, err
	}
	label := library.AgeRatingLabel(country, stored[ref].ByCountry[country])
	return optional(label), nil
}

// gatedRefs leaves out the titles the gate holds back, keeping the order.
func (r *Resolver) gatedRefs(ctx context.Context, gate ageGate, refs []library.MediaRef) ([]library.MediaRef, error) {
	allowed, err := r.allowedRefs(ctx, gate, refs)
	if err != nil || allowed == nil {
		return refs, err
	}
	kept := make([]library.MediaRef, 0, len(refs))
	for _, ref := range refs {
		if allowed[ref] {
			kept = append(kept, ref)
		}
	}
	return kept, nil
}

// gatedContinueItems leaves out the continue entries whose title the gate holds back.
func (r *Resolver) gatedContinueItems(ctx context.Context, gate ageGate, entries []*model.ContinueItem) ([]*model.ContinueItem, error) {
	refs := make([]library.MediaRef, 0, len(entries))
	for _, entry := range entries {
		refs = append(refs, titleRef(entry.Title))
	}
	allowed, err := r.allowedRefs(ctx, gate, refs)
	if err != nil || allowed == nil {
		return entries, err
	}
	kept := make([]*model.ContinueItem, 0, len(entries))
	for _, entry := range entries {
		if allowed[titleRef(entry.Title)] {
			kept = append(kept, entry)
		}
	}
	return kept, nil
}

// gatedItems leaves out the recommendations the gate holds back.
func (r *Resolver) gatedItems(ctx context.Context, gate ageGate, items []recommend.Item) ([]recommend.Item, error) {
	allowed, err := r.allowedRefs(ctx, gate, itemRefs(items))
	if err != nil || allowed == nil {
		return items, err
	}
	kept := make([]recommend.Item, 0, len(items))
	for _, item := range items {
		if allowed[item.Media] {
			kept = append(kept, item)
		}
	}
	return kept, nil
}

// gatedRows leaves out the recommendations the gate holds back, from every row.
func (r *Resolver) gatedRows(ctx context.Context, gate ageGate, rows []recommend.Row) ([]recommend.Row, error) {
	if !gate.active {
		return rows, nil
	}
	gated := make([]recommend.Row, 0, len(rows))
	for _, row := range rows {
		items, err := r.gatedItems(ctx, gate, row.Items)
		if err != nil {
			return nil, err
		}
		row.Items = items
		gated = append(gated, row)
	}
	return gated, nil
}

// visibleTitleModels maps stored titles and leaves out the ones the request's profile may not see.
func (r *Resolver) visibleTitleModels(ctx context.Context, titles []library.Title) ([]*model.Title, error) {
	return r.visibleTitles(ctx, titleModels(titles))
}

// requireAllowedMedia fails for a movie or episode the request's profile may not see.
func (r *Resolver) requireAllowedMedia(ctx context.Context, media []library.MediaRef) error {
	gate, err := r.requestGate(ctx)
	if err != nil {
		return err
	}
	return r.requireGateAllows(ctx, gate, media)
}

func (r *Resolver) requireGateAllows(ctx context.Context, gate ageGate, media []library.MediaRef) error {
	if !gate.active {
		return nil
	}
	titles, err := r.seriesOrMovieRefs(ctx, media)
	if err != nil {
		return err
	}
	allowed, err := r.allowedRefs(ctx, gate, titles)
	if err != nil {
		return err
	}
	for _, ref := range titles {
		if !allowed[ref] {
			return errNotForProfile
		}
	}
	return nil
}

// seriesOrMovieRefs turns episodes into their series, the title that carries the rating.
func (r *Resolver) seriesOrMovieRefs(ctx context.Context, media []library.MediaRef) ([]library.MediaRef, error) {
	titles := make([]library.MediaRef, 0, len(media))
	for _, ref := range media {
		if ref.MediaType != library.MediaTypeEpisode {
			titles = append(titles, ref)
			continue
		}
		episode, err := r.Library.Store.Episode(ctx, ref.MediaID)
		if err != nil {
			return nil, err
		}
		if episode != nil {
			titles = append(titles, library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: episode.SeriesID})
		}
	}
	return titles, nil
}
