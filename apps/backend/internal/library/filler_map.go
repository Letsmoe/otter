package library

import (
	"slices"

	"github.com/Letsmoe/otter/internal/plugins"
)

// mapFillerToEpisodes gives each stored episode the kind a filler list names for it, by
// TMDB episode id.
//
// Lists number episodes across the whole show (absolute numbering), TMDB by season. The list's
// number N is taken to be the Nth regular episode when the seasons are put in order (specials,
// season 0, are left out). That holds when TMDB's seasons are a plain split of the show's
// episodes in air order, which is the usual case for anime. When the list reaches past the last
// episode TMDB knows, the two number the show differently (TMDB merges or splits episodes, or is
// behind), the match would be off by an unknown amount and nothing is mapped.
//
// A list that names its seasons (SeasonNumber set) is matched by season and episode number.
func mapFillerToEpisodes(entries []plugins.FillerEpisode, episodes []Episode) map[int]string {
	regular := regularEpisodes(episodes)
	if namesSeasons(entries) {
		return mapBySeason(entries, regular)
	}
	return mapByPosition(entries, regular)
}

func regularEpisodes(episodes []Episode) []Episode {
	regular := make([]Episode, 0, len(episodes))
	for _, episode := range episodes {
		if episode.SeasonNumber > 0 {
			regular = append(regular, episode)
		}
	}
	slices.SortFunc(regular, func(first, second Episode) int {
		if first.SeasonNumber != second.SeasonNumber {
			return first.SeasonNumber - second.SeasonNumber
		}
		return first.EpisodeNumber - second.EpisodeNumber
	})
	return regular
}

func namesSeasons(entries []plugins.FillerEpisode) bool {
	for _, entry := range entries {
		if entry.SeasonNumber > 0 {
			return true
		}
	}
	return false
}

func isFillerKind(kind string) bool {
	return kind == plugins.FillerKindFiller || kind == plugins.FillerKindMixed || kind == plugins.FillerKindCanon
}

func mapByPosition(entries []plugins.FillerEpisode, regular []Episode) map[int]string {
	kinds := map[int]string{}
	for _, entry := range entries {
		if entry.Number > len(regular) {
			return map[int]string{}
		}
		if entry.Number >= 1 && isFillerKind(entry.Kind) {
			kinds[regular[entry.Number-1].TMDBID] = entry.Kind
		}
	}
	return kinds
}

func mapBySeason(entries []plugins.FillerEpisode, regular []Episode) map[int]string {
	type position struct{ season, number int }
	known := make(map[position]int, len(regular))
	for _, episode := range regular {
		known[position{episode.SeasonNumber, episode.EpisodeNumber}] = episode.TMDBID
	}
	kinds := map[int]string{}
	for _, entry := range entries {
		episodeID, found := known[position{entry.SeasonNumber, entry.Number}]
		if found && isFillerKind(entry.Kind) {
			kinds[episodeID] = entry.Kind
		}
	}
	return kinds
}
