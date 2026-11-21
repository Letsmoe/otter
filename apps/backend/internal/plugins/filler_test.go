package plugins

import (
	"context"
	"testing"
)

func TestFillerRefusedWithoutCapability(t *testing.T) {
	plugin := &Plugin{Meta: PluginMeta{Name: "streams-only", Capabilities: []Capability{CapabilityStreams}}}
	if _, err := NewEmbeddedRunner().Filler(context.Background(), plugin, FillerArgs{Title: "Naruto"}); err == nil {
		t.Fatal("expected an error for a plugin without the filler capability")
	}
}
