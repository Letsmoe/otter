// Package franchise builds watch-order guides: the films of a TMDB
// collection in release order with one profile's progress through them.
package franchise

import (
	"time"

	"github.com/Letsmoe/otter/internal/library"
)

// Status is how far a profile got through one film.
type Status string

const (
	StatusWatched    Status = "watched"
	StatusInProgress Status = "in_progress"
	StatusNotYet     Status = "not_yet"
)

// completedFraction is the share of the runtime that counts as seen; the
// rest is usually credits (same as the profiles package).
const completedFraction = 0.9

// Watch is what a profile recorded of one film.
type Watch struct {
	Completed       bool
	ProgressSeconds float64
	DurationSeconds float64
}

// Part is one film of a guide.
type Part struct {
	Title library.Title
	// Number is the place in the watch order, from 1.
	Number int
	Status Status
	// Progress is the watched share (0..1) of a film that is in progress.
	Progress float64
	// Released is false for films that are announced but not out yet.
	Released bool
}

// Guide is a collection with the profile's progress through it.
type Guide struct {
	Collection library.Collection
	Parts      []Part
}

// NewGuide numbers the films in the order given and sets each one's status
// from the profile's watches, keyed by film id. today decides what is out.
func NewGuide(collection library.Collection, titles []library.Title, watches map[int]Watch, today time.Time) Guide {
	parts := make([]Part, 0, len(titles))
	for index, title := range titles {
		status, progress := statusOf(watches[title.TMDBID])
		parts = append(parts, Part{
			Title: title, Number: index + 1, Status: status, Progress: progress,
			Released: isReleased(title.ReleaseDate, today),
		})
	}
	return Guide{Collection: collection, Parts: parts}
}

func statusOf(watch Watch) (Status, float64) {
	fraction := 0.0
	if watch.DurationSeconds > 0 {
		fraction = min(1, watch.ProgressSeconds/watch.DurationSeconds)
	}
	if watch.Completed || fraction >= completedFraction {
		return StatusWatched, 1
	}
	if watch.ProgressSeconds > 0 {
		return StatusInProgress, fraction
	}
	return StatusNotYet, 0
}

// isReleased reports whether a film's release date (YYYY-MM-DD) is today or
// earlier. A missing date means not out yet.
func isReleased(releaseDate string, today time.Time) bool {
	if releaseDate == "" {
		return false
	}
	return releaseDate <= today.Format("2006-01-02")
}

// WatchedCount is how many films the profile finished.
func (guide Guide) WatchedCount() int {
	count := 0
	for _, part := range guide.Parts {
		if part.Status == StatusWatched {
			count++
		}
	}
	return count
}

// Next is the film to continue with: the first released film that isn't
// finished, so one in progress comes before later ones. Nil when everything
// out is watched.
func (guide Guide) Next() *Part {
	for index := range guide.Parts {
		part := &guide.Parts[index]
		if part.Released && part.Status != StatusWatched {
			return part
		}
	}
	return nil
}

// Started reports whether the profile watched any of it.
func (guide Guide) Started() bool {
	for _, part := range guide.Parts {
		if part.Status != StatusNotYet {
			return true
		}
	}
	return false
}
