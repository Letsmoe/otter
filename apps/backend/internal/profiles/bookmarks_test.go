package profiles

import (
	"context"
	"strings"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

func movieRef(id int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: id}
}

func episodeRef(id int) library.MediaRef {
	return library.MediaRef{MediaType: library.MediaTypeEpisode, MediaID: id}
}

func TestBookmarksListEarliestMomentFirstPerProfile(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	for _, position := range []float64{900, 60} {
		if _, err := service.AddBookmark(ctx, 1, movieRef(603), position, "Wake up, Neo"); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := service.AddBookmark(ctx, 2, movieRef(603), 5, ""); err != nil {
		t.Fatal(err)
	}

	bookmarks, err := service.Bookmarks(ctx, 1, movieRef(603))
	if err != nil {
		t.Fatal(err)
	}
	if len(bookmarks) != 2 || bookmarks[0].PositionSeconds != 60 || bookmarks[1].PositionSeconds != 900 {
		t.Fatalf("unexpected bookmarks: %+v", bookmarks)
	}
	if bookmarks[0].Caption != "Wake up, Neo" {
		t.Fatalf("caption not kept: %+v", bookmarks[0])
	}
}

func TestBookmarksOfASeriesFollowTheEpisodeOrder(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	for _, episode := range []struct{ id, season, number int }{{11, 1, 1}, {12, 1, 2}, {21, 2, 1}} {
		_, err := service.database.Exec(ctx, `INSERT INTO episode (tmdb_id, series_id, season_number, episode_number)
			VALUES ($id, 1396, $season, $number)`, db.Vars{"id": episode.id, "season": episode.season, "number": episode.number})
		if err != nil {
			t.Fatal(err)
		}
	}
	for _, id := range []int{21, 11, 12} {
		if _, err := service.AddBookmark(ctx, 1, episodeRef(id), 30, "line"); err != nil {
			t.Fatal(err)
		}
	}

	bookmarks, err := service.Bookmarks(ctx, 1, library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: 1396})
	if err != nil {
		t.Fatal(err)
	}
	var order []int
	for _, bookmark := range bookmarks {
		order = append(order, bookmark.MediaID)
	}
	if len(order) != 3 || order[0] != 11 || order[1] != 12 || order[2] != 21 {
		t.Fatalf("unexpected order: %v", order)
	}
}

func TestRemovingABookmarkLeavesOtherProfilesBookmarksAlone(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	mine, err := service.AddBookmark(ctx, 1, movieRef(603), 10, "")
	if err != nil {
		t.Fatal(err)
	}

	if err := service.RemoveBookmark(ctx, 2, mine.ID); err != nil {
		t.Fatal(err)
	}
	stillThere, _ := service.Bookmarks(ctx, 1, movieRef(603))
	if len(stillThere) != 1 {
		t.Fatalf("another profile removed it: %+v", stillThere)
	}

	if err := service.RemoveBookmark(ctx, 1, mine.ID); err != nil {
		t.Fatal(err)
	}
	gone, _ := service.Bookmarks(ctx, 1, movieRef(603))
	if len(gone) != 0 {
		t.Fatalf("not removed: %+v", gone)
	}
}

func TestOnlyMoviesAndEpisodesWithAPositionCanBeBookmarked(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	series := library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: 1396}
	if _, err := service.AddBookmark(ctx, 1, series, 10, ""); err == nil {
		t.Fatal("a series was bookmarked")
	}
	if _, err := service.AddBookmark(ctx, 1, movieRef(603), -1, ""); err == nil {
		t.Fatal("a negative position was bookmarked")
	}
}

func TestALongCaptionIsCut(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	bookmark, err := service.AddBookmark(ctx, 1, movieRef(603), 10, "  "+strings.Repeat("é", 500))
	if err != nil {
		t.Fatal(err)
	}
	if got := len([]rune(bookmark.Caption)); got != maxBookmarkCaption {
		t.Fatalf("caption has %d characters", got)
	}
}
