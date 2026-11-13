package neoworks

import _ "embed"

// descriptor is the @letsmoe/otter descriptor exactly as the registry publishes
// it (packages/otter-schema/generated/schema.descriptor.json); libneoworks
// refuses content until the registry confirms these bytes. TestTheDescriptorIsThePublishedOne
// fails when the copy is stale.
//
//go:embed collection/descriptor.json
var descriptor []byte

// schemaVersion is the published version the descriptor belongs to
// (packages/otter-schema/openschema.yaml); every row Otter writes records it.
const schemaVersion = "0.1.0"

// Tables of the collection (packages/otter-schema/otter.schema).
const (
	tableProfile        = "Profile"
	tableWatchProgress  = "WatchProgress"
	tableWatchlistEntry = "WatchlistEntry"
	tableRating         = "Rating"
	tableSettings       = "Settings"
)
