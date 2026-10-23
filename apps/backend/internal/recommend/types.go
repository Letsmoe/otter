package recommend

import (
	"strconv"
	"strings"

	"github.com/neoworks/otter/internal/library"
)

// Candidate source names. Every recommended item carries the one it was
// served from.
const (
	SourceRelatedTMDB   = "related:tmdb"
	SourceRelatedPeople = "related:people"
	SourceCast          = "affinity:cast"
	SourceDirector      = "affinity:director"
	SourceGenre         = "affinity:genre"
	SourceRandom        = "explore:random"
	SourcePopular       = "popular"
)

// Reason kinds: why an item was recommended.
const (
	ReasonRelated = "related" // EntityID: seed "movie:603"
	ReasonPerson  = "person"  // EntityID: person TMDB id
	ReasonGenre   = "genre"   // EntityID: genre name
	ReasonRandom  = "random"
	ReasonPopular = "popular"
)

// Reason explains one recommendation, e.g. "Because you watched Heat".
type Reason struct {
	Kind       string
	EntityID   string
	EntityName string
	Text       string
}

// Key identifies the reason's group, used to build rows.
func (reason Reason) Key() string {
	if reason.EntityID == "" {
		return reason.Kind
	}
	return reason.Kind + ":" + reason.EntityID
}

// describe fills the human-readable text from the kind and entity name.
func (reason *Reason) describe() {
	switch reason.Kind {
	case ReasonRelated:
		reason.Text = "Because you watched " + reason.EntityName
	case ReasonPerson:
		reason.Text = "More with " + reason.EntityName
	case ReasonGenre:
		reason.Text = "Because you like " + reason.EntityName
	case ReasonRandom:
		reason.Text = "Something different"
	default:
		reason.Text = "Popular"
	}
}

// Item is one ranked recommendation.
type Item struct {
	Media  library.MediaRef
	Source string
	Reason Reason
	Score  float64
}

// Row is a titled group of items sharing a reason.
type Row struct {
	Key    string
	Title  string
	Reason Reason
	Items  []Item
}

func isExploration(source string) bool {
	return strings.HasPrefix(source, "explore:")
}

// refKey renders a ref as "movie:603" for reason entity ids.
func refKey(ref library.MediaRef) string {
	return string(ref.MediaType) + ":" + strconv.Itoa(ref.MediaID)
}
