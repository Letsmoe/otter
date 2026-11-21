// Package letterboxd brings a Letterboxd account into Otter: the export ZIP
// (watched, ratings, diary and watchlist) is matched to TMDB and applied to a
// profile, and the public diary feed is read for what was logged since.
// Everything here only reads from Letterboxd; writing to it needs API access
// Letterboxd gives out on request only.
package letterboxd

import (
	"archive/zip"
	"bytes"
	"encoding/csv"
	"errors"
	"fmt"
	"io"
	"path"
	"strconv"
	"strings"
)

// maxExportBytes bounds what is read from one file of an export.
const maxExportBytes = 64 << 20

// Film is a film as the export names it.
type Film struct {
	Name string
	Year int
	// URI is the film's Letterboxd address (boxd.it/...), stable across renames.
	URI string
}

// Key identifies the film within an export.
func (f Film) Key() string {
	if f.URI != "" {
		return f.URI
	}
	return fmt.Sprintf("%s|%d", strings.ToLower(f.Name), f.Year)
}

// Entry is one row of the export: a film with the date it was logged and,
// for the diary and ratings, what was given.
type Entry struct {
	Film
	// Date is when the row was written (YYYY-MM-DD).
	Date string
	// WatchedDate is the diary's day of watching; empty outside the diary.
	WatchedDate string
	// Stars is 0.5 to 5 in half steps; 0 when unrated.
	Stars   float64
	Rewatch bool
}

// Export is a Letterboxd export ZIP, read.
type Export struct {
	Watched   []Entry
	Ratings   []Entry
	Diary     []Entry
	Watchlist []Entry
}

// ReadExport reads the CSV files of an export ZIP. Files may sit at the top
// or in a folder; missing ones are skipped, but a ZIP with none of them is
// not an export.
func ReadExport(zipBytes []byte) (*Export, error) {
	archive, err := zip.NewReader(bytes.NewReader(zipBytes), int64(len(zipBytes)))
	if err != nil {
		return nil, errors.New("that is not a ZIP file")
	}
	export := &Export{}
	found := 0
	for _, file := range archive.File {
		target := targetOf(export, path.Base(strings.ToLower(file.Name)))
		if target == nil {
			continue
		}
		entries, err := readEntries(file)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", file.Name, err)
		}
		*target = entries
		found++
	}
	if found == 0 {
		return nil, errors.New("this ZIP has no watched.csv, ratings.csv, diary.csv or watchlist.csv: is it a Letterboxd export?")
	}
	return export, nil
}

func targetOf(export *Export, name string) *[]Entry {
	switch name {
	case "watched.csv":
		return &export.Watched
	case "ratings.csv":
		return &export.Ratings
	case "diary.csv":
		return &export.Diary
	case "watchlist.csv":
		return &export.Watchlist
	}
	return nil
}

func readEntries(file *zip.File) ([]Entry, error) {
	opened, err := file.Open()
	if err != nil {
		return nil, err
	}
	defer opened.Close()
	content, err := io.ReadAll(io.LimitReader(opened, maxExportBytes))
	if err != nil {
		return nil, err
	}
	return parseEntries(content)
}

// parseEntries reads one export CSV by its header names, so column order and
// extra columns do not matter.
func parseEntries(content []byte) ([]Entry, error) {
	content = bytes.TrimPrefix(content, []byte("\xef\xbb\xbf"))
	reader := csv.NewReader(bytes.NewReader(content))
	reader.FieldsPerRecord = -1
	rows, err := reader.ReadAll()
	if err != nil {
		return nil, err
	}
	if len(rows) == 0 {
		return nil, nil
	}
	columns := columnIndexes(rows[0])
	entries := make([]Entry, 0, len(rows)-1)
	for _, row := range rows[1:] {
		entry, ok := entryOf(columns, row)
		if ok {
			entries = append(entries, entry)
		}
	}
	return entries, nil
}

func columnIndexes(header []string) map[string]int {
	columns := make(map[string]int, len(header))
	for index, name := range header {
		columns[strings.ToLower(strings.TrimSpace(name))] = index
	}
	return columns
}

func cell(columns map[string]int, row []string, name string) string {
	index, found := columns[name]
	if !found || index >= len(row) {
		return ""
	}
	return strings.TrimSpace(row[index])
}

func entryOf(columns map[string]int, row []string) (Entry, bool) {
	name := cell(columns, row, "name")
	if name == "" {
		return Entry{}, false
	}
	year, _ := strconv.Atoi(cell(columns, row, "year"))
	stars, _ := strconv.ParseFloat(cell(columns, row, "rating"), 64)
	return Entry{
		Film:        Film{Name: name, Year: year, URI: cell(columns, row, "letterboxd uri")},
		Date:        cell(columns, row, "date"),
		WatchedDate: cell(columns, row, "watched date"),
		Stars:       validStars(stars),
		Rewatch:     strings.EqualFold(cell(columns, row, "rewatch"), "yes"),
	}, true
}

// validStars keeps ratings from 0.5 to 5; anything else means unrated.
func validStars(stars float64) float64 {
	if stars < 0.5 || stars > 5 {
		return 0
	}
	return stars
}

// OtterRating maps Letterboxd's 0.5 to 5 stars onto Otter's 1 to 10: two
// points a star, so 2.5 stars is the neutral 5 and 1 star or less reads as
// a dislike.
func OtterRating(stars float64) float64 {
	return stars * 2
}

// Films lists every film of the export once, in the order first seen.
func (e *Export) Films() []Film {
	seen := map[string]bool{}
	var films []Film
	for _, entries := range [][]Entry{e.Watched, e.Ratings, e.Diary, e.Watchlist} {
		for _, entry := range entries {
			if seen[entry.Key()] {
				continue
			}
			seen[entry.Key()] = true
			films = append(films, entry.Film)
		}
	}
	return films
}
