package plugins

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/netdns"
)

// testBundle mimics what the SDK's runPlugin does in a bundle: read the
// request from Bun.stdin, fetch, answer through fs.writeSync, then exit.
const testBundle = `
const { writeSync } = require("fs");
async function main() {
  const input = JSON.parse(await Bun.stdin.text());
  const response = await fetch(input.args.url + "?q=" + encodeURIComponent(new URL(input.args.url).host), {
    headers: { "X-Plugin": process.env.PLUGIN_TOKEN, "Accept-Encoding": "br" },
    signal: AbortSignal.timeout(5000),
  });
  const data = await response.json();
  writeSync(1, JSON.stringify({ result: { status: response.status, type: response.headers.get("content-type"), data } }) + "\n");
  process.exit(0);
  writeSync(1, "written after exit\n");
}
main().catch((error) => {
  writeSync(1, JSON.stringify({ error: String(error) }) + "\n");
  process.exit(0);
});
`

// writeBundle stores a bundle as a plugin directory and returns its Plugin.
func writeBundle(t *testing.T, source string) *Plugin {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "plugin.js"), []byte(source), 0o644); err != nil {
		t.Fatal(err)
	}
	return &Plugin{Dir: dir, EntryPoint: "plugin.js", Meta: PluginMeta{Name: "test"}}
}

// TestEmbeddedRunnerRoundTrip runs a bundle through the embedded runtime:
// stdin, env, fetch with headers, JSON body, stdout, and exit.
func TestEmbeddedRunnerRoundTrip(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"query":    r.URL.Query().Get("q"),
			"token":    r.Header.Get("X-Plugin"),
			"encoding": r.Header.Get("Accept-Encoding"),
		})
	}))
	defer server.Close()

	runner := NewEmbeddedRunner()
	runner.SetEnv([]string{"PLUGIN_TOKEN=secret"})
	raw, err := runner.invoke(context.Background(), writeBundle(t, testBundle), "test", map[string]string{"url": server.URL})
	if err != nil {
		t.Fatal(err)
	}

	var result struct {
		Status int               `json:"status"`
		Type   string            `json:"type"`
		Data   map[string]string `json:"data"`
	}
	if err := json.Unmarshal(raw, &result); err != nil {
		t.Fatal(err)
	}
	if result.Status != 200 || result.Type != "application/json" {
		t.Errorf("status %d type %q", result.Status, result.Type)
	}
	if result.Data["token"] != "secret" {
		t.Errorf("env header not sent: %v", result.Data)
	}
	if !strings.HasPrefix(result.Data["query"], "127.0.0.1:") {
		t.Errorf("URL global or query encoding broken: %v", result.Data)
	}
	if result.Data["encoding"] == "br" {
		t.Errorf("plugin's Accept-Encoding should be left to the transport")
	}
}

// TestEmbeddedRunnerTimeout stops a plugin that never finishes.
func TestEmbeddedRunnerTimeout(t *testing.T) {
	plugin := writeBundle(t, `while (true) {}`)
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	if _, err := NewEmbeddedRunner().invoke(ctx, plugin, "meta", nil); err == nil {
		t.Fatal("expected the busy loop to be interrupted")
	}
}

// TestEmbeddedRunnerProvidesBuffer checks the Node Buffer global resolvers
// use to decode base64-obfuscated stream sources.
func TestEmbeddedRunnerProvidesBuffer(t *testing.T) {
	plugin := writeBundle(t, `
const { writeSync } = require("fs");
const decoded = Buffer.from("aHR0cHM6Ly9jZG4vYS5tM3U4", "base64").toString("utf-8");
writeSync(1, JSON.stringify({ result: decoded }) + "\n");
process.exit(0);
`)
	raw, err := NewEmbeddedRunner().invoke(context.Background(), plugin, "test", nil)
	if err != nil {
		t.Fatal(err)
	}
	if string(raw) != `"https://cdn/a.m3u8"` {
		t.Fatalf("decoded %s", raw)
	}
}

// TestEmbeddedBundlesLive runs the real plugin bundles against the live sites.
// Opt-in: OTTER_EMBEDDED_BUNDLES=<dir from apps/backend/scripts/bundle-plugins.ts>.
func TestEmbeddedBundlesLive(t *testing.T) {
	bundles := os.Getenv("OTTER_EMBEDDED_BUNDLES")
	if bundles == "" {
		t.Skip("OTTER_EMBEDDED_BUNDLES not set")
	}
	netdns.Configure(os.Getenv("DNS_SERVERS"), "")
	runner := NewEmbeddedRunner()
	entries, err := os.ReadDir(bundles)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		t.Run(entry.Name(), func(t *testing.T) {
			checkBundleLive(t, runner, filepath.Join(bundles, entry.Name()))
		})
	}
}

// liveTestMovie is a title every streaming site carries.
var liveTestMovie = StreamsArgs{
	MediaType: MediaTypeMovie,
	Title:     "Matrix",
	Year:      1999,
	TMDBID:    603,
	IMDBID:    "tt0133093",
}

// checkBundleLive loads one bundle's meta and, for streams plugins, looks up
// liveTestMovie, logging how long each step took.
func checkBundleLive(t *testing.T, runner *Runner, dir string) {
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	started := time.Now()
	meta, err := runner.loadMeta(ctx, dir, "plugin.js")
	if err != nil {
		t.Fatalf("meta: %v", err)
	}
	t.Logf("meta %s in %s", meta.Name, time.Since(started))
	if meta.RequiresSolver {
		t.Skip("needs FlareSolverr")
	}
	if !meta.Has(CapabilityStreams) {
		return
	}
	plugin := &Plugin{Dir: dir, EntryPoint: "plugin.js", Meta: meta}
	started = time.Now()
	result, err := runner.Streams(ctx, plugin, liveTestMovie)
	if err != nil {
		t.Fatalf("streams: %v", err)
	}
	t.Logf("streams → %d links in %s", len(result.Downloads), time.Since(started))
}
