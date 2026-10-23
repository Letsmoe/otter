package plugins

import (
	"context"
	"testing"
)

const subtitlesEntry = "../../../../packages/otter-sdk/tests/fixtures/subtitles/index.ts"

func bundleSubtitlesPlugin(t *testing.T) (*Runner, *Plugin) {
	t.Helper()
	plugin := bundleEntry(t, subtitlesEntry)
	runner := NewEmbeddedRunner()
	meta, err := runner.loadMeta(context.Background(), plugin.Dir, plugin.EntryPoint)
	if err != nil {
		t.Fatalf("load meta: %v", err)
	}
	plugin.Meta = meta
	return runner, plugin
}

func TestSubtitlesThroughEmbeddedRuntime(t *testing.T) {
	runner, plugin := bundleSubtitlesPlugin(t)
	if !plugin.Meta.Has(CapabilitySubtitles) || plugin.Meta.Has(CapabilityStreams) {
		t.Fatalf("capabilities: %v", plugin.Meta.Capabilities)
	}
	args := SubtitlesArgs{MediaType: MediaTypeMovie, Title: "Dark", TMDBID: 1, Languages: []string{"en"}}
	result, err := runner.Subtitles(context.Background(), plugin, args)
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Subtitles) != 1 {
		t.Fatalf("subtitles: %+v", result.Subtitles)
	}
	file := result.Subtitles[0]
	if file.Language != "en" || file.Format != "vtt" || !file.HearingImpaired || file.URL != "https://subs.example/en.vtt" {
		t.Errorf("unexpected file: %+v", file)
	}
}

func TestSubtitlesRefusedWithoutCapability(t *testing.T) {
	plugin := &Plugin{Meta: PluginMeta{Name: "streams-only", Capabilities: []Capability{CapabilityStreams}}}
	if _, err := NewEmbeddedRunner().Subtitles(context.Background(), plugin, SubtitlesArgs{}); err == nil {
		t.Fatal("expected an error for a plugin without the subtitles capability")
	}
}
