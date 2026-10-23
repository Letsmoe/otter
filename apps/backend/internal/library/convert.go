package library

import (
	"slices"

	"github.com/neoworks/otter/internal/tmdb"
)

// maxCastStored caps the stored cast; recommendations only weigh top billing.
const maxCastStored = 20

// storedCrewJobs are the crew credits worth keeping for display and taste.
var storedCrewJobs = []string{"Director", "Screenplay", "Writer", "Creator", "Original Music Composer"}

func movieTitle(detail *tmdb.MovieDetail) Title {
	return Title{
		MediaType:     MediaTypeMovie,
		TMDBID:        detail.ID,
		IMDBID:        detail.IMDBID,
		Title:         detail.Title,
		OriginalTitle: detail.OriginalTitle,
		Overview:      detail.Overview,
		Tagline:       detail.Tagline,
		ReleaseDate:   detail.ReleaseDate,
		Runtime:       detail.Runtime,
		Status:        detail.Status,
		Popularity:    detail.Popularity,
		VoteAverage:   detail.VoteAverage,
		VoteCount:     detail.VoteCount,
		PosterPath:    detail.PosterPath,
		BackdropPath:  detail.BackdropPath,
		LogoPath:      pickLogo(detail.Images.Logos),
		TrailerKey:    pickTrailer(detail.Videos.Results),
		Genres:        genreNames(detail.Genres),
		Keywords:      detail.Keywords.Names(),
		Adult:         detail.Adult,
	}
}

func seriesTitle(detail *tmdb.SeriesDetail) Title {
	return Title{
		MediaType:     MediaTypeSeries,
		TMDBID:        detail.ID,
		IMDBID:        detail.ExternalIDs.IMDBID,
		Title:         detail.Name,
		OriginalTitle: detail.OriginalName,
		Overview:      detail.Overview,
		Tagline:       detail.Tagline,
		ReleaseDate:   detail.FirstAirDate,
		Status:        detail.Status,
		Popularity:    detail.Popularity,
		VoteAverage:   detail.VoteAverage,
		VoteCount:     detail.VoteCount,
		PosterPath:    detail.PosterPath,
		BackdropPath:  detail.BackdropPath,
		LogoPath:      pickLogo(detail.Images.Logos),
		TrailerKey:    pickTrailer(detail.Videos.Results),
		Genres:        genreNames(detail.Genres),
		Keywords:      detail.Keywords.Names(),
		Adult:         detail.Adult,
	}
}

// stubTitle converts a list entry. mediaType decides whether Title or Name
// and ReleaseDate or FirstAirDate apply.
func stubTitle(mediaType MediaType, result tmdb.SearchResult, genres []string) Title {
	stub := Title{
		MediaType:    mediaType,
		TMDBID:       result.ID,
		Title:        result.Title,
		Overview:     result.Overview,
		ReleaseDate:  result.ReleaseDate,
		Popularity:   result.Popularity,
		VoteAverage:  result.VoteAverage,
		VoteCount:    result.VoteCount,
		PosterPath:   result.PosterPath,
		BackdropPath: result.BackdropPath,
		Genres:       genres,
		Keywords:     []string{},
		Adult:        result.Adult,
	}
	if mediaType == MediaTypeSeries {
		stub.Title = result.Name
		stub.ReleaseDate = result.FirstAirDate
	}
	return stub
}

func genreNames(genres []tmdb.Genre) []string {
	names := make([]string, 0, len(genres))
	for _, genre := range genres {
		names = append(names, genre.Name)
	}
	return names
}

func titleCredits(credits tmdb.Credits) []Credit {
	result := []Credit{}
	for _, member := range credits.Cast {
		if member.Order >= maxCastStored {
			continue
		}
		result = append(result, Credit{
			PersonID: member.ID, Role: RoleCast, Character: member.Character,
			Order: member.Order, Name: member.Name, ProfilePath: member.ProfilePath,
		})
	}
	for index, member := range credits.Crew {
		if !slices.Contains(storedCrewJobs, member.Job) {
			continue
		}
		result = append(result, Credit{
			PersonID: member.ID, Role: RoleCrew, Job: member.Job,
			Order: index, Name: member.Name, ProfilePath: member.ProfilePath,
		})
	}
	return result
}

// pickTrailer prefers an official YouTube trailer, then any YouTube trailer,
// then any YouTube teaser.
func pickTrailer(videos []tmdb.Video) string {
	preferences := []func(tmdb.Video) bool{
		func(video tmdb.Video) bool { return video.Type == "Trailer" && video.Official },
		func(video tmdb.Video) bool { return video.Type == "Trailer" },
		func(video tmdb.Video) bool { return video.Type == "Teaser" },
	}
	for _, matches := range preferences {
		for _, video := range videos {
			if video.Site == "YouTube" && matches(video) {
				return video.Key
			}
		}
	}
	return ""
}

// pickLogo prefers an English logo, then a language-neutral one.
func pickLogo(logos []tmdb.Image) string {
	for _, language := range []string{"en", ""} {
		for _, logo := range logos {
			if logo.ISO6391 == language {
				return logo.FilePath
			}
		}
	}
	return ""
}

func seasonRows(seriesID int, summaries []tmdb.SeasonSummary) []Season {
	seasons := make([]Season, 0, len(summaries))
	for _, summary := range summaries {
		seasons = append(seasons, Season{
			TMDBID: summary.ID, SeriesID: seriesID, SeasonNumber: summary.SeasonNumber,
			Name: summary.Name, Overview: summary.Overview, AirDate: summary.AirDate,
			PosterPath: summary.PosterPath, EpisodeCount: summary.EpisodeCount,
		})
	}
	return seasons
}

func episodeRows(seriesID int, summaries []tmdb.EpisodeSummary) []Episode {
	episodes := make([]Episode, 0, len(summaries))
	for _, summary := range summaries {
		episodes = append(episodes, Episode{
			TMDBID: summary.ID, SeriesID: seriesID, SeasonNumber: summary.SeasonNumber,
			EpisodeNumber: summary.EpisodeNumber, Name: summary.Name,
			Overview: summary.Overview, AirDate: summary.AirDate, Runtime: summary.Runtime,
			StillPath: summary.StillPath, VoteAverage: summary.VoteAverage,
		})
	}
	return episodes
}

func personRow(detail *tmdb.PersonDetail) Person {
	return Person{
		TMDBID: detail.ID, IMDBID: detail.IMDBID, Name: detail.Name,
		ProfilePath: detail.ProfilePath, Biography: detail.Biography,
		Birthday: detail.Birthday, Deathday: detail.Deathday,
		PlaceOfBirth: detail.PlaceOfBirth, KnownForDepartment: detail.KnownForDepartment,
		Popularity: detail.Popularity,
	}
}
