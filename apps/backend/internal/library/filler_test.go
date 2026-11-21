package library

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/plugins"
)

// Two seasons of three episodes, and a special that the list's numbering ignores.
func animeEpisodes() []Episode {
	return []Episode{
		{TMDBID: 21, SeriesID: 1, SeasonNumber: 2, EpisodeNumber: 1},
		{TMDBID: 13, SeriesID: 1, SeasonNumber: 1, EpisodeNumber: 3},
		{TMDBID: 11, SeriesID: 1, SeasonNumber: 1, EpisodeNumber: 1},
		{TMDBID: 22, SeriesID: 1, SeasonNumber: 2, EpisodeNumber: 2},
		{TMDBID: 12, SeriesID: 1, SeasonNumber: 1, EpisodeNumber: 2},
		{TMDBID: 23, SeriesID: 1, SeasonNumber: 2, EpisodeNumber: 3},
		{TMDBID: 1, SeriesID: 1, SeasonNumber: 0, EpisodeNumber: 1},
	}
}

func TestAbsoluteNumbersCountAcrossSeasonsInOrder(t *testing.T) {
	entries := []plugins.FillerEpisode{
		{Number: 1, Kind: "canon"},
		{Number: 2, Kind: "filler"},
		{Number: 4, Kind: "mixed"},
		{Number: 6, Kind: "filler"},
	}

	kinds := mapFillerToEpisodes(entries, animeEpisodes())

	want := map[int]string{11: "canon", 12: "filler", 21: "mixed", 23: "filler"}
	if len(kinds) != len(want) {
		t.Fatalf("kinds: %v", kinds)
	}
	for id, kind := range want {
		if kinds[id] != kind {
			t.Errorf("episode %d: got %q, want %q", id, kinds[id], kind)
		}
	}
}

func TestAListLongerThanTMDBMapsNothing(t *testing.T) {
	entries := []plugins.FillerEpisode{{Number: 1, Kind: "filler"}, {Number: 7, Kind: "filler"}}

	if kinds := mapFillerToEpisodes(entries, animeEpisodes()); len(kinds) != 0 {
		t.Fatalf("mapped despite the mismatch: %v", kinds)
	}
}

func TestAListShorterThanTMDBLeavesTheNewestEpisodesUnmarked(t *testing.T) {
	entries := []plugins.FillerEpisode{{Number: 1, Kind: "filler"}}

	kinds := mapFillerToEpisodes(entries, animeEpisodes())

	if len(kinds) != 1 || kinds[11] != "filler" {
		t.Fatalf("kinds: %v", kinds)
	}
}

func TestAListThatNamesSeasonsIsMatchedBySeasonAndNumber(t *testing.T) {
	entries := []plugins.FillerEpisode{
		{Number: 2, Kind: "filler", SeasonNumber: 2},
		{Number: 9, Kind: "filler", SeasonNumber: 2},
		{Number: 1, Kind: "canon", SeasonNumber: 1},
	}

	kinds := mapFillerToEpisodes(entries, animeEpisodes())

	if len(kinds) != 2 || kinds[22] != "filler" || kinds[11] != "canon" {
		t.Fatalf("kinds: %v", kinds)
	}
}

func TestUnknownKindsAreIgnored(t *testing.T) {
	entries := []plugins.FillerEpisode{{Number: 1, Kind: "recap"}, {Number: 0, Kind: "filler"}, {Number: 2, Kind: "filler"}}

	kinds := mapFillerToEpisodes(entries, animeEpisodes())

	if len(kinds) != 1 || kinds[12] != "filler" {
		t.Fatalf("kinds: %v", kinds)
	}
}

func TestFillerMarksAreStoredPerEpisodeAndSurviveRefetchingTheSeason(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	if err := store.SaveEpisodes(ctx, 1, 1, animeEpisodes()[1:2]); err != nil {
		t.Fatal(err)
	}
	if err := store.SetEpisodeFiller(ctx, 1, map[int]string{13: "filler"}); err != nil {
		t.Fatal(err)
	}
	if err := store.SaveEpisodes(ctx, 1, 1, animeEpisodes()[1:2]); err != nil {
		t.Fatal(err)
	}

	episode, err := store.Episode(ctx, 13)
	if err != nil || episode == nil {
		t.Fatalf("episode: %v %v", episode, err)
	}
	if episode.Filler != "filler" {
		t.Fatalf("filler: %q", episode.Filler)
	}
	if err := store.SetEpisodeFiller(ctx, 1, map[int]string{}); err != nil {
		t.Fatal(err)
	}
	episode, _ = store.Episode(ctx, 13)
	if episode.Filler != "" {
		t.Fatalf("mark not cleared: %q", episode.Filler)
	}
}

func TestTheListIsKeptAsAnswered(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	if stored, err := store.Filler(ctx, 1); err != nil || stored != nil {
		t.Fatalf("expected nothing before asking: %v %v", stored, err)
	}
	entries := []plugins.FillerEpisode{{Number: 5, Kind: "filler"}, {Number: 6, Kind: "mixed", SeasonNumber: 2}}
	if err := store.SaveFiller(ctx, 1, entries); err != nil {
		t.Fatal(err)
	}

	stored, err := store.Filler(ctx, 1)
	if err != nil || stored == nil {
		t.Fatalf("stored: %v %v", stored, err)
	}
	if len(stored.Entries) != 2 || stored.Entries[1] != entries[1] || stored.FetchedAt == "" {
		t.Fatalf("stored: %+v", stored)
	}
	if err := store.SaveFiller(ctx, 1, nil); err != nil {
		t.Fatal(err)
	}
	stored, _ = store.Filler(ctx, 1)
	if len(stored.Entries) != 0 {
		t.Fatalf("an empty answer wasn't kept: %+v", stored)
	}
}

func TestOnlyAnimatedSeriesAreLookedUp(t *testing.T) {
	if !isAnimation(&Title{Genres: []string{"Action & Adventure", "animation"}}) {
		t.Fatal("an animated series was skipped")
	}
	if isAnimation(&Title{Genres: []string{"Drama"}}) {
		t.Fatal("a drama was looked up")
	}
}
