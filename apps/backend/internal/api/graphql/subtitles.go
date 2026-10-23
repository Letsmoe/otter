package graphql

import (
	"context"
	"strings"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/profiles"
	"github.com/neoworks/otter/internal/subtitles"
)

// defaultCaptionLanguages are asked for when no profile says otherwise; they
// match a new profile's audio languages.
var defaultCaptionLanguages = []string{"de", "en"}

// captionLanguages are the languages to offer captions in for a profile:
// its caption language first, then its audio languages.
func (r *Resolver) captionLanguages(ctx context.Context, profileID *string) ([]string, error) {
	if profileID == nil {
		return defaultCaptionLanguages, nil
	}
	number, err := parseProfile(*profileID)
	if err != nil {
		return nil, err
	}
	profile, err := r.Profiles.Get(ctx, number)
	if err != nil {
		return nil, err
	}
	return subtitles.PreferredLanguages(profile.CaptionLanguage(), profile.LanguageList()), nil
}

func subtitleModel(file subtitles.Subtitle) *model.Subtitle {
	return &model.Subtitle{
		ID:              file.ID,
		Language:        file.Language,
		Label:           file.Label,
		Release:         optional(file.Release),
		HearingImpaired: file.HearingImpaired,
		Source:          file.Plugin,
		VttURL:          subtitles.RoutePrefix + file.ID + ".vtt",
		SrtURL:          subtitles.RoutePrefix + file.ID + ".srt",
	}
}

func modelSubtitleSize(size string) model.SubtitleSize {
	return model.SubtitleSize(strings.ToUpper(size))
}

// profileSubtitleSize maps an optional size argument; nil leaves it unchanged.
func profileSubtitleSize(size *model.SubtitleSize) *string {
	if size == nil {
		return nil
	}
	lowered := strings.ToLower(string(*size))
	return &lowered
}

func captionUpdate(subtitlesEnabled *bool, subtitleLanguage *string, subtitleSize *model.SubtitleSize, voiceBoost *bool) profiles.Update {
	return profiles.Update{
		SubtitlesEnabled: subtitlesEnabled,
		SubtitleLanguage: subtitleLanguage,
		SubtitleSize:     profileSubtitleSize(subtitleSize),
		VoiceBoost:       voiceBoost,
	}
}
