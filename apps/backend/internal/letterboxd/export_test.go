package letterboxd

import (
	"archive/zip"
	"bytes"
	"testing"
)

func buildZip(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for name, content := range files {
		file, err := writer.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := file.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

const sampleWatched = "Date,Name,Year,Letterboxd URI\n" +
	"2024-01-05,Heat,1995,https://boxd.it/2aXe\n" +
	"2024-02-10,\"Amélie, ou le fabuleux destin\",2001,https://boxd.it/2b0o\n"

const sampleRatings = "Date,Name,Year,Letterboxd URI,Rating\n" +
	"2024-01-06,Heat,1995,https://boxd.it/2aXe,4.5\n" +
	"2024-02-11,Alien,1979,https://boxd.it/2a9c,0\n"

const sampleDiary = "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n" +
	"2024-01-06,Heat,1995,https://boxd.it/2aXe,4.5,,,2024-01-05\n" +
	"2024-03-01,Heat,1995,https://boxd.it/2aXe,5,Yes,,2024-02-28\n"

const sampleWatchlist = "Date,Name,Year,Letterboxd URI\n" +
	"2024-04-01,Stalker,1979,https://boxd.it/2bnU\n"

func TestReadExportFindsTheFilesAnywhereInTheZip(t *testing.T) {
	export, err := ReadExport(buildZip(t, map[string]string{
		"letterboxd-ann-2024/watched.csv":     sampleWatched,
		"letterboxd-ann-2024/ratings.csv":     sampleRatings,
		"letterboxd-ann-2024/diary.csv":       sampleDiary,
		"letterboxd-ann-2024/watchlist.csv":   sampleWatchlist,
		"letterboxd-ann-2024/likes/films.csv": "Date,Name,Year,Letterboxd URI\n",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if len(export.Watched) != 2 || len(export.Ratings) != 2 || len(export.Diary) != 2 || len(export.Watchlist) != 1 {
		t.Fatalf("read %d watched, %d ratings, %d diary, %d watchlist", len(export.Watched), len(export.Ratings), len(export.Diary), len(export.Watchlist))
	}
	amelie := export.Watched[1]
	if amelie.Name != "Amélie, ou le fabuleux destin" || amelie.Year != 2001 || amelie.URI != "https://boxd.it/2b0o" || amelie.Date != "2024-02-10" {
		t.Fatalf("quoted name misread: %+v", amelie)
	}
}

func TestReadExportReadsStarsRewatchesAndWatchedDates(t *testing.T) {
	export, err := ReadExport(buildZip(t, map[string]string{"ratings.csv": sampleRatings, "diary.csv": sampleDiary}))
	if err != nil {
		t.Fatal(err)
	}
	if export.Ratings[0].Stars != 4.5 || export.Ratings[1].Stars != 0 {
		t.Errorf("stars: %v, %v", export.Ratings[0].Stars, export.Ratings[1].Stars)
	}
	rewatch := export.Diary[1]
	if !rewatch.Rewatch || rewatch.WatchedDate != "2024-02-28" || rewatch.Stars != 5 {
		t.Errorf("diary rewatch misread: %+v", rewatch)
	}
}

func TestReadExportIgnoresAByteOrderMarkAndColumnOrder(t *testing.T) {
	content := "\xef\xbb\xbfName,Letterboxd URI,Year,Date\nHeat,https://boxd.it/2aXe,1995,2024-01-05\n"
	export, err := ReadExport(buildZip(t, map[string]string{"watched.csv": content}))
	if err != nil {
		t.Fatal(err)
	}
	heat := export.Watched[0]
	if heat.Name != "Heat" || heat.Year != 1995 || heat.Date != "2024-01-05" {
		t.Fatalf("misread: %+v", heat)
	}
}

func TestReadExportRefusesWhatIsNoExport(t *testing.T) {
	if _, err := ReadExport([]byte("not a zip")); err == nil {
		t.Error("a text file was accepted")
	}
	if _, err := ReadExport(buildZip(t, map[string]string{"photos/cat.jpg": "x"})); err == nil {
		t.Error("a ZIP without any export file was accepted")
	}
}

func TestStarsMapOntoOtterRatings(t *testing.T) {
	cases := map[float64]float64{0.5: 1, 2.5: 5, 3.5: 7, 5: 10}
	for stars, want := range cases {
		if got := OtterRating(stars); got != want {
			t.Errorf("OtterRating(%v) = %v, want %v", stars, got, want)
		}
	}
}

func TestStarsOutsideTheScaleCountAsUnrated(t *testing.T) {
	for _, stars := range []float64{0, 0.2, 5.5, -1} {
		if validStars(stars) != 0 {
			t.Errorf("%v stars were kept", stars)
		}
	}
}

func TestFilmsListsEachFilmOnce(t *testing.T) {
	export, err := ReadExport(buildZip(t, map[string]string{
		"watched.csv": sampleWatched, "ratings.csv": sampleRatings, "diary.csv": sampleDiary, "watchlist.csv": sampleWatchlist,
	}))
	if err != nil {
		t.Fatal(err)
	}
	// Heat, Amélie, Alien, Stalker.
	if films := export.Films(); len(films) != 4 {
		t.Fatalf("%d films: %+v", len(films), films)
	}
}

func TestWatchedDaysUseTheDiaryAndWidenForRewatches(t *testing.T) {
	export, err := ReadExport(buildZip(t, map[string]string{"watched.csv": sampleWatched, "diary.csv": sampleDiary}))
	if err != nil {
		t.Fatal(err)
	}
	days := watchedDays(export)
	heat := days["https://boxd.it/2aXe"]
	if heat.first != "2024-01-05" || heat.last != "2024-02-28" {
		t.Errorf("heat watched %+v", heat)
	}
	amelie := days["https://boxd.it/2b0o"]
	if amelie.first != "2024-02-10" || amelie.last != "2024-02-10" {
		t.Errorf("a film only in watched.csv uses the day it was marked: %+v", amelie)
	}
}

func TestTheNewestRatingWins(t *testing.T) {
	export, err := ReadExport(buildZip(t, map[string]string{"ratings.csv": sampleRatings, "diary.csv": sampleDiary}))
	if err != nil {
		t.Fatal(err)
	}
	ratings := latestRatings(export)
	heat := ratings["https://boxd.it/2aXe"]
	if heat.stars != 5 || heat.day != "2024-02-28" {
		t.Errorf("heat rated %+v, want the rewatch's 5 stars", heat)
	}
	if _, rated := ratings["https://boxd.it/2a9c"]; rated {
		t.Error("a 0 rating counts as unrated")
	}
}
