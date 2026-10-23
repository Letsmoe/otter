package library

import (
	"testing"

	"github.com/neoworks/otter/internal/tmdb"
)

func TestThemeOptionsRestrictThemesToTheirMediaType(t *testing.T) {
	if _, applies := themeOptions(MediaTypeSeries, CatalogThemeBlockbusters); applies {
		t.Fatal("blockbusters are movies only")
	}
	if _, applies := themeOptions(MediaTypeMovie, CatalogThemeBingeworthy); applies {
		t.Fatal("bingeworthy is series only")
	}
	if _, applies := themeOptions(MediaTypeMovie, "unknown"); applies {
		t.Fatal("unknown themes don't apply")
	}
}

func TestThemeOptionsQueries(t *testing.T) {
	gems, _ := themeOptions(MediaTypeMovie, CatalogThemeHiddenGems)
	if gems.MinRating != 7.5 || gems.MinVotes != 200 || gems.MaxVotes != 2000 {
		t.Fatalf("unexpected hidden gems query %+v", gems)
	}
	feelGood, _ := themeOptions(MediaTypeSeries, CatalogThemeFeelGood)
	if feelGood.GenreIDs != "35|10751" || feelGood.MinRating != 7 {
		t.Fatalf("unexpected feel-good query %+v", feelGood)
	}
	blockbusters, _ := themeOptions(MediaTypeMovie, CatalogThemeBlockbusters)
	if blockbusters.Sort != tmdb.SortRevenue {
		t.Fatalf("blockbusters should sort by revenue, got %s", blockbusters.Sort)
	}
}
