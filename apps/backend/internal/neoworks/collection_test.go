package neoworks

import (
	"bytes"
	"os"
	"strings"
	"testing"
)

const schemaDirectory = "../../../../packages/otter-schema/"

func TestTheDescriptorIsThePublishedOne(t *testing.T) {
	published, err := os.ReadFile(schemaDirectory + "generated/schema.descriptor.json")
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(published, descriptor) {
		t.Fatal("internal/neoworks/collection/descriptor.json differs from packages/otter-schema/generated; copy it again")
	}
}

func TestTheSchemaVersionIsTheManifests(t *testing.T) {
	manifest, err := os.ReadFile(schemaDirectory + "openschema.yaml")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(manifest), "\nversion: "+schemaVersion+"\n") {
		t.Fatalf("schemaVersion %s is not the version in openschema.yaml", schemaVersion)
	}
}
