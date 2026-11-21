package letterboxd

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

const sampleFeed = `<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:letterboxd="https://letterboxd.com" xmlns:tmdb="https://themoviedb.org" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>Letterboxd - Ann</title>
    <item>
      <title>Heat, 1995 - ★★★★½</title>
      <link>https://letterboxd.com/ann/film/heat-1995/</link>
      <pubDate>Sat, 14 Sep 2024 10:07:22 +1200</pubDate>
      <letterboxd:watchedDate>2024-09-13</letterboxd:watchedDate>
      <letterboxd:rewatch>No</letterboxd:rewatch>
      <letterboxd:filmTitle>Heat</letterboxd:filmTitle>
      <letterboxd:filmYear>1995</letterboxd:filmYear>
      <letterboxd:memberRating>4.5</letterboxd:memberRating>
      <tmdb:movieId>949</tmdb:movieId>
    </item>
    <item>
      <title>Stalker, 1979</title>
      <link>https://letterboxd.com/ann/film/stalker/</link>
      <pubDate>Fri, 13 Sep 2024 23:30:00 -1200</pubDate>
      <letterboxd:rewatch>Yes</letterboxd:rewatch>
      <letterboxd:filmTitle>Stalker</letterboxd:filmTitle>
      <letterboxd:filmYear>1979</letterboxd:filmYear>
      <tmdb:movieId>1398</tmdb:movieId>
    </item>
    <item>
      <title>My favourite heists</title>
      <link>https://letterboxd.com/ann/list/heists/</link>
      <pubDate>Fri, 13 Sep 2024 10:00:00 +1200</pubDate>
    </item>
    <item>
      <title>Severance, 2022</title>
      <link>https://letterboxd.com/ann/film/x/</link>
      <letterboxd:filmTitle>Severance</letterboxd:filmTitle>
      <tmdb:tvId>95396</tmdb:tvId>
    </item>
  </channel>
</rss>`

func TestParseFeedReadsFilmsWithTheirDatesAndRatings(t *testing.T) {
	items, err := ParseFeed([]byte(sampleFeed))
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 {
		t.Fatalf("%d items, want the two films: %+v", len(items), items)
	}
	heat := items[0]
	if heat.TMDBID != 949 || heat.Name != "Heat" || heat.Year != 1995 || heat.Stars != 4.5 || heat.Date != "2024-09-13" || heat.Rewatch {
		t.Errorf("heat misread: %+v", heat)
	}
	stalker := items[1]
	if stalker.TMDBID != 1398 || stalker.Stars != 0 || !stalker.Rewatch {
		t.Errorf("stalker misread: %+v", stalker)
	}
	// Published Friday 23:30 at UTC-12, which is Saturday in UTC.
	if stalker.Date != "2024-09-14" {
		t.Errorf("an entry without a watched day uses its publication day, got %q", stalker.Date)
	}
}

func TestParseFeedRefusesWhatIsNoXML(t *testing.T) {
	if _, err := ParseFeed([]byte("<html><body>Just a moment...")); err == nil {
		t.Fatal("a bot-check page was read as a feed")
	}
}

func TestUsernamesComeFromNamesAndProfileAddresses(t *testing.T) {
	good := map[string]string{
		"ann":                              "ann",
		"  @ann_b ":                        "ann_b",
		"https://letterboxd.com/ann/":      "ann",
		"https://letterboxd.com/ann/films": "ann",
		"https://www.letterboxd.com/Ann":   "Ann",
	}
	for input, want := range good {
		got, err := NormalizeUsername(input)
		if err != nil || got != want {
			t.Errorf("NormalizeUsername(%q) = %q, %v; want %q", input, got, err, want)
		}
	}
	for _, input := range []string{"", "a b", "https://example.com/ann", "ann/../x@y"} {
		if _, err := NormalizeUsername(input); err == nil {
			t.Errorf("NormalizeUsername(%q) was accepted", input)
		}
	}
}

func TestFetchFeedReadsAPublicDiary(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/ann/rss/" {
			http.NotFound(writer, request)
			return
		}
		writer.Header().Set("Content-Type", "application/rss+xml")
		_, _ = writer.Write([]byte(sampleFeed))
	}))
	defer server.Close()

	items, err := fetchFeedFrom(context.Background(), server.Client(), server.URL+"/ann/rss/")
	if err != nil || len(items) != 2 {
		t.Fatalf("items %d, err %v", len(items), err)
	}
	if _, err := fetchFeedFrom(context.Background(), server.Client(), server.URL+"/nobody/rss/"); err != ErrFeedNotFound {
		t.Fatalf("err = %v, want ErrFeedNotFound", err)
	}
}

func TestFilmPagesNameTheirTMDBMovie(t *testing.T) {
	page := `<body class="film" data-tmdb-id="949" data-tmdb-type="movie">`
	if id, ok := tmdbMovieIDIn(page); !ok || id != 949 {
		t.Errorf("id %d ok %v", id, ok)
	}
	if _, ok := tmdbMovieIDIn(`<body data-tmdb-id="1" data-tmdb-type="tv">`); ok {
		t.Error("a TV page was taken for a movie")
	}
	if _, ok := tmdbMovieIDIn("<body>"); ok {
		t.Error("a page without an id matched")
	}
}

func TestOnlyLetterboxdAddressesAreFetched(t *testing.T) {
	for _, address := range []string{"https://boxd.it/2aXe", "https://letterboxd.com/film/heat-1995/"} {
		if !isLetterboxdAddress(address) {
			t.Errorf("%s refused", address)
		}
	}
	for _, address := range []string{"http://boxd.it/2aXe", "https://evil.example/boxd.it", "https://letterboxd.com.evil.example/"} {
		if isLetterboxdAddress(address) {
			t.Errorf("%s accepted", address)
		}
	}
}
