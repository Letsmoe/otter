package letterboxd

import (
	"context"
	"strconv"
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"

	"github.com/Letsmoe/otter/internal/tmdb"
)

// MovieFinder is what matching needs from TMDB.
type MovieFinder interface {
	SearchMovieByTitle(ctx context.Context, title string, year int, language string) ([]tmdb.SearchResult, error)
}

// Match finds the TMDB movie a film means: by title and year in the
// configured language first (TMDB then also knows the original title), then
// by its English title, then ignoring the year, which Letterboxd and TMDB
// sometimes count differently. ok is false when nothing fits.
func Match(ctx context.Context, finder MovieFinder, film Film) (tmdb.SearchResult, bool, error) {
	for _, attempt := range searchAttempts(film) {
		found, err := finder.SearchMovieByTitle(ctx, film.Name, attempt.year, attempt.language)
		if err != nil {
			return tmdb.SearchResult{}, false, err
		}
		best, matched := chooseMatch(film, found)
		if matched {
			return best, true, nil
		}
	}
	return tmdb.SearchResult{}, false, nil
}

type searchAttempt struct {
	year     int
	language string
}

func searchAttempts(film Film) []searchAttempt {
	if film.Year == 0 {
		return []searchAttempt{{0, ""}, {0, "en-US"}}
	}
	return []searchAttempt{{film.Year, ""}, {film.Year, "en-US"}, {0, "en-US"}}
}

// chooseMatch picks the result with the film's title (as shown or in its
// original language) from the film's year, or a year either side; without a
// year the most popular one wins. Several fits go to the closest year, then
// the more popular film.
func chooseMatch(film Film, results []tmdb.SearchResult) (tmdb.SearchResult, bool) {
	wanted := normalizeTitle(film.Name)
	var best tmdb.SearchResult
	bestFound := false
	for _, result := range results {
		if result.Adult || !hasTitle(result, wanted) || !yearFits(film.Year, result) {
			continue
		}
		if !bestFound || betterMatch(film.Year, result, best) {
			best = result
			bestFound = true
		}
	}
	return best, bestFound
}

func hasTitle(result tmdb.SearchResult, wanted string) bool {
	if wanted == "" {
		return false
	}
	return normalizeTitle(result.Title) == wanted || normalizeTitle(result.OriginalTitle) == wanted
}

func yearFits(filmYear int, result tmdb.SearchResult) bool {
	if filmYear == 0 {
		return true
	}
	return yearDistance(filmYear, result) <= 1
}

// yearDistance is how many years apart the film and the result are; a result
// without a date counts as one apart, and every result fits a film without a year.
func yearDistance(filmYear int, result tmdb.SearchResult) int {
	released := releaseYear(result)
	if filmYear == 0 {
		return 0
	}
	if released == 0 {
		return 1
	}
	if released > filmYear {
		return released - filmYear
	}
	return filmYear - released
}

func betterMatch(filmYear int, candidate, current tmdb.SearchResult) bool {
	candidateDistance := yearDistance(filmYear, candidate)
	currentDistance := yearDistance(filmYear, current)
	if candidateDistance != currentDistance {
		return candidateDistance < currentDistance
	}
	return candidate.Popularity > current.Popularity
}

func releaseYear(result tmdb.SearchResult) int {
	if len(result.ReleaseDate) < 4 {
		return 0
	}
	year, err := strconv.Atoi(result.ReleaseDate[:4])
	if err != nil {
		return 0
	}
	return year
}

// normalizeTitle lowers a title, drops accents and punctuation and writes
// "&" as "and", so "Amélie" and "Amelie", "Se7en" and "Se7en!" compare equal.
func normalizeTitle(title string) string {
	decomposed := norm.NFD.String(strings.ReplaceAll(strings.ToLower(title), "&", " and "))
	var builder strings.Builder
	for _, character := range decomposed {
		switch {
		case unicode.Is(unicode.Mn, character):
			continue
		case unicode.IsLetter(character) || unicode.IsDigit(character):
			builder.WriteRune(character)
		default:
			builder.WriteRune(' ')
		}
	}
	return strings.Join(strings.Fields(builder.String()), " ")
}
