package graphql

import (
	"testing"

	"github.com/vektah/gqlparser/v2/ast"
	"github.com/vektah/gqlparser/v2/parser"
)

func firstOperation(t *testing.T, document string) *ast.OperationDefinition {
	t.Helper()
	parsed, err := parser.ParseQuery(&ast.Source{Input: document})
	if err != nil {
		t.Fatal(err)
	}
	return parsed.Operations[0]
}

func TestChangesToSyncedDataTriggerASync(t *testing.T) {
	for _, document := range []string{
		`mutation { setRating(profileId: "profile:1", mediaId: "movie:603", rating: 10) }`,
		`mutation Save { recordProgress(profileId: "profile:1", mediaId: "movie:603", progressSeconds: 60, durationSeconds: 7200) { completed } }`,
		`mutation { findStreams(id: "movie:603") updateSettings(input: {requireVpn: true}) { requireVpn } }`,
	} {
		if !changesSyncedData(firstOperation(t, document)) {
			t.Errorf("no sync for %s", document)
		}
	}
}

func TestOtherOperationsDoNotSync(t *testing.T) {
	for _, document := range []string{
		`mutation { findStreams(id: "movie:603") }`,
		`mutation { syncNeoworks { signedIn } }`,
	} {
		if changesSyncedData(firstOperation(t, document)) {
			t.Errorf("sync triggered by %s", document)
		}
	}
}
