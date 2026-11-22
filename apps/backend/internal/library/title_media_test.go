package library

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/tmdb"
)

func TestPickVideosKeepsYouTubeTrailersFirst(t *testing.T) {
	videos := []tmdb.Video{
		{Site: "YouTube", Type: "Clip", Key: "clip", Name: "A clip"},
		{Site: "Vimeo", Type: "Trailer", Key: "vimeo", Name: "Elsewhere"},
		{Site: "YouTube", Type: "Trailer", Key: "trailer", Name: "Official trailer"},
		{Site: "YouTube", Type: "Bloopers", Key: "bloopers", Name: "Bloopers"},
	}
	picked := pickVideos(videos)
	if len(picked) != 2 || picked[0].Key != "trailer" || picked[1].Key != "clip" {
		t.Fatalf("picked %+v, want the trailer then the clip", picked)
	}
}

func TestPickScenesSkipsBackdropsWithText(t *testing.T) {
	backdrops := []tmdb.Image{
		{FilePath: "/titled.jpg", ISO6391: "en"},
		{FilePath: "/scene.jpg"},
	}
	scenes := pickScenes(backdrops)
	if len(scenes) != 1 || scenes[0] != "/scene.jpg" {
		t.Fatalf("scenes %v, want only the one without a language", scenes)
	}
}

func TestTitleMediaRoundTrips(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	movie := Title{
		MediaType: MediaTypeMovie, TMDBID: 603, Title: "The Matrix",
		Videos:     []TitleVideo{{Key: "trailer", Name: "Official trailer", Type: "Trailer"}},
		ScenePaths: []string{"/scene.jpg"},
	}
	if err := store.SaveMovie(ctx, movie); err != nil {
		t.Fatal(err)
	}
	stored, err := store.Title(ctx, movie.Ref())
	if err != nil {
		t.Fatal(err)
	}
	if len(stored.Videos) != 1 || stored.Videos[0].Key != "trailer" || len(stored.ScenePaths) != 1 {
		t.Fatalf("stored videos %+v and scenes %v", stored.Videos, stored.ScenePaths)
	}
}
