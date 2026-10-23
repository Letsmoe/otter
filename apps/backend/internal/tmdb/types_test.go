package tmdb

import (
	"encoding/json"
	"testing"
)

// TestSeriesDetailSeasons verifies the series detail parses the `seasons` array
// (GET /tv/{id}) that drives canonical season creation.
func TestSeriesDetailSeasons(t *testing.T) {
	const payload = `{
		"id": 1399,
		"name": "Game of Thrones",
		"number_of_seasons": 8,
		"seasons": [
			{"id": 3624, "season_number": 0, "name": "Specials", "episode_count": 14},
			{"id": 3625, "season_number": 1, "name": "Season 1", "overview": "Nine noble families.", "air_date": "2011-04-17", "poster_path": "/poster.jpg", "episode_count": 10, "vote_average": 8.3}
		]
	}`

	var detail SeriesDetail
	if err := json.Unmarshal([]byte(payload), &detail); err != nil {
		t.Fatalf("unmarshal series detail: %v", err)
	}
	if len(detail.Seasons) != 2 {
		t.Fatalf("expected 2 seasons, got %d", len(detail.Seasons))
	}
	first := detail.Seasons[1]
	if first.SeasonNumber != 1 || first.EpisodeCount != 10 || first.ID != 3625 {
		t.Fatalf("season 1 fields wrong: %+v", first)
	}
	if first.AirDate != "2011-04-17" || first.VoteAverage != 8.3 {
		t.Fatalf("season 1 metadata wrong: %+v", first)
	}
}

// TestSeasonDetailEpisodes verifies the season detail parses the `episodes`
// array (GET /tv/{id}/season/{n}) that drives canonical episode creation.
func TestSeasonDetailEpisodes(t *testing.T) {
	const payload = `{
		"id": 3625,
		"season_number": 1,
		"name": "Season 1",
		"episodes": [
			{"id": 63056, "episode_number": 1, "season_number": 1, "name": "Winter Is Coming", "overview": "Jon Arryn is dead.", "air_date": "2011-04-17", "runtime": 62, "still_path": "/still.jpg", "production_code": "101", "vote_average": 8.1, "vote_count": 300}
		],
		"credits": {"cast": []}
	}`

	var detail SeasonDetail
	if err := json.Unmarshal([]byte(payload), &detail); err != nil {
		t.Fatalf("unmarshal season detail: %v", err)
	}
	if len(detail.Episodes) != 1 {
		t.Fatalf("expected 1 episode, got %d", len(detail.Episodes))
	}
	episode := detail.Episodes[0]
	if episode.ID != 63056 || episode.EpisodeNumber != 1 || episode.Runtime != 62 {
		t.Fatalf("episode fields wrong: %+v", episode)
	}
	if episode.StillPath != "/still.jpg" || episode.ProductionCode != "101" || episode.VoteCount != 300 {
		t.Fatalf("episode metadata wrong: %+v", episode)
	}
}
