package graphql

import (
	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/profiles"
	"github.com/neoworks/otter/internal/settings"
	"github.com/neoworks/otter/internal/skips"
	"github.com/neoworks/otter/internal/tmdb"
)

// optional turns a zero value into nil for nullable fields.
func optional[Value comparable](value Value) *Value {
	var zero Value
	if value == zero {
		return nil
	}
	return &value
}

func titleModel(title library.Title) *model.Title {
	return &model.Title{
		ID:            mediaID(library.MediaRef{MediaType: title.MediaType, MediaID: title.TMDBID}),
		MediaType:     modelMediaType(title.MediaType),
		TmdbID:        title.TMDBID,
		ImdbID:        optional(title.IMDBID),
		Title:         title.Title,
		OriginalTitle: optional(title.OriginalTitle),
		Overview:      optional(title.Overview),
		Tagline:       optional(title.Tagline),
		ReleaseDate:   optional(title.ReleaseDate),
		Year:          optional(title.Year()),
		Runtime:       optional(title.Runtime),
		Status:        optional(title.Status),
		VoteAverage:   title.VoteAverage,
		VoteCount:     title.VoteCount,
		Popularity:    title.Popularity,
		PosterPath:    optional(title.PosterPath),
		BackdropPath:  optional(title.BackdropPath),
		LogoPath:      optional(title.LogoPath),
		TrailerKey:    optional(title.TrailerKey),
		Genres:        nonNil(title.Genres),
		Keywords:      nonNil(title.Keywords),
		DetailFetched: title.DetailFetchedAt != "",
	}
}

func titleModels(titles []library.Title) []*model.Title {
	models := make([]*model.Title, 0, len(titles))
	for _, title := range titles {
		models = append(models, titleModel(title))
	}
	return models
}

func nonNil(values []string) []string {
	if values == nil {
		return []string{}
	}
	return values
}

func seasonModel(season library.Season) *model.Season {
	return &model.Season{
		ID:           formatID("season", season.TMDBID),
		SeasonNumber: season.SeasonNumber,
		Name:         optional(season.Name),
		Overview:     optional(season.Overview),
		AirDate:      optional(season.AirDate),
		PosterPath:   optional(season.PosterPath),
		EpisodeCount: season.EpisodeCount,
	}
}

func episodeModel(episode library.Episode) *model.Episode {
	return &model.Episode{
		ID:            formatID(string(library.MediaTypeEpisode), episode.TMDBID),
		TmdbID:        episode.TMDBID,
		SeasonNumber:  episode.SeasonNumber,
		EpisodeNumber: episode.EpisodeNumber,
		Name:          optional(episode.Name),
		Overview:      optional(episode.Overview),
		AirDate:       optional(episode.AirDate),
		Runtime:       optional(episode.Runtime),
		StillPath:     optional(episode.StillPath),
		VoteAverage:   episode.VoteAverage,
	}
}

func personModel(person library.Person) *model.Person {
	return &model.Person{
		ID:                 formatID(kindPerson, person.TMDBID),
		TmdbID:             person.TMDBID,
		Name:               person.Name,
		ProfilePath:        optional(person.ProfilePath),
		Biography:          optional(person.Biography),
		Birthday:           optional(person.Birthday),
		Deathday:           optional(person.Deathday),
		PlaceOfBirth:       optional(person.PlaceOfBirth),
		KnownForDepartment: optional(person.KnownForDepartment),
	}
}

func creditModel(credit library.Credit) *model.Credit {
	return &model.Credit{
		Person: &model.Person{
			ID:          formatID(kindPerson, credit.PersonID),
			TmdbID:      credit.PersonID,
			Name:        credit.Name,
			ProfilePath: optional(credit.ProfilePath),
		},
		Role:      credit.Role,
		Job:       optional(credit.Job),
		Character: optional(credit.Character),
		Order:     credit.Order,
	}
}

func streamModels(streams []library.Stream) []*model.Stream {
	models := make([]*model.Stream, 0, len(streams))
	for _, stream := range streams {
		models = append(models, &model.Stream{
			ID:        formatID(kindStream, stream.ID),
			Plugin:    stream.Plugin,
			URL:       stream.URL,
			Label:     optional(stream.Label),
			Quality:   optional(stream.Quality),
			Language:  optional(stream.Language),
			Format:    optional(stream.Format),
			SizeBytes: optional(float64(stream.SizeBytes)),
			SpeedBps:  optional(stream.SpeedBps),
			Playable:  stream.Playable,
			FoundAt:   stream.FoundAt,
		})
	}
	return models
}

func progressModel(entry *profiles.WatchEntry) *model.WatchProgress {
	if entry == nil {
		return nil
	}
	return &model.WatchProgress{
		MediaID:         mediaID(entry.Ref()),
		ProgressSeconds: entry.ProgressSeconds,
		DurationSeconds: entry.DurationSeconds,
		Completed:       entry.Completed,
		UpdatedAt:       entry.UpdatedAt,
	}
}

func profileModel(profile profiles.Profile) *model.Profile {
	return &model.Profile{
		ID:               formatID(kindProfile, profile.ID),
		Name:             profile.Name,
		AvatarURL:        optional(profile.AvatarURL),
		HasPin:           profile.HasPin(),
		IsChild:          profile.IsChild,
		Languages:        profile.LanguageList(),
		SubtitlesEnabled: profile.SubtitlesEnabled,
		SubtitleLanguage: optional(profile.SubtitleLanguage),
		SubtitleSize:     modelSubtitleSize(profile.SubtitleSize),
		VoiceBoost:       profile.VoiceBoost,
	}
}

var tmdbKeyStatuses = map[tmdb.KeyStatus]model.TmdbKeyStatus{
	tmdb.KeyStatusOK:      model.TmdbKeyStatusOk,
	tmdb.KeyStatusMissing: model.TmdbKeyStatusMissing,
	tmdb.KeyStatusInvalid: model.TmdbKeyStatusInvalid,
}

func (r *Resolver) settingsModel(current settings.Settings) *model.Settings {
	disabled := current.DisabledPlugins
	if disabled == nil {
		disabled = []string{}
	}
	return &model.Settings{
		TmdbAPIKey:         current.TMDBApiKey,
		TmdbLanguage:       current.TMDBLanguage,
		TmdbKeyStatus:      tmdbKeyStatuses[r.TMDB.KeyStatus()],
		UsesDefaultTmdbKey: r.Settings.UsesDefaultTMDBKey(),
		TmdbKeyPromptSeen:  current.TMDBKeyPromptSeen,
		RequireVpn:         current.RequireVpn,
		DisabledPlugins:    disabled,
	}
}

func pluginModel(plugin *plugins.Plugin, registry *plugins.Registry, values map[string]string) *model.Plugin {
	capabilities := make([]string, 0, len(plugin.Meta.Capabilities))
	for _, capability := range plugin.Meta.Capabilities {
		capabilities = append(capabilities, string(capability))
	}
	settingFields := plugin.Meta.SettingFields()
	fields := make([]*model.PluginSettingField, 0, len(settingFields))
	for _, field := range settingFields {
		fields = append(fields, &model.PluginSettingField{
			Key: field.Key, Label: field.Label, Description: optional(field.Description),
			Type: field.Type, Required: field.Required, Default: optional(field.Default),
			Value: optional(values[field.Key]),
		})
	}
	return &model.Plugin{
		Name:         plugin.Meta.Name,
		DisplayName:  optional(plugin.Meta.DisplayName),
		Description:  optional(plugin.Meta.Description),
		Icon:         optional(plugin.Meta.Icon),
		Version:      plugin.Meta.Version,
		Capabilities: capabilities,
		Enabled:      registry.IsEnabled(plugin.Meta.Name),
		Available:    registry.IsAvailable(plugin.Meta.Name),
		Settings:     fields,
	}
}

func skipSegmentModels(segments []skips.Segment) []*model.SkipSegment {
	models := make([]*model.SkipSegment, 0, len(segments))
	for _, segment := range segments {
		models = append(models, &model.SkipSegment{Kind: segment.Kind, StartSeconds: segment.StartSeconds, EndSeconds: segment.EndSeconds})
	}
	return models
}
