package plugins

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
)

// sampleEntry is the v2 sample plugin the SDK's tests also run under Bun.
const sampleEntry = "../../../../packages/otter-sdk/tests/fixtures/sample/index.ts"

// packedPlayer is Dean Edwards packer output hiding
// https://cdn.example/hls/master.m3u8?t=9 in a player script.
const packedPlayer = `<script>eval(function(p,a,c,k,e,d){e=function(c){return(c<a?'':e(parseInt(c/a)))+((c=c%a)>35?String.fromCharCode(c+29):c.toString(36))};if(!''.replace(/^/,String)){while(c--){d[e(c)]=k[c]||e(c)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('0 1 = 2("3"); 1.4({5:[{6:"7://8.9/a/b.c?d=e"}]}); 0 f = g + h + i + j + k + l + m;',62,23,'var|player|jwplayer|v|setup|sources|file|https|cdn|example|hls|master|m3u8|t|9|n|10|20|30|40|50|60|70'.split('|'),0,{}))</script>`

const cloudflareChallenge = `<html><title>Just a moment...</title><script src="/cdn-cgi/challenge-platform/h/b/orchestrate"></script>Cloudflare</html>`

const hostersEntry = "../../../../packages/otter-sdk/tests/fixtures/hosters/index.ts"

// bundleSample builds the sample plugin the way scripts/lib/bundle.ts does.
func bundleSample(t *testing.T) *Plugin {
	t.Helper()
	plugin := bundleEntry(t, sampleEntry)
	meta, err := NewEmbeddedRunner().loadMeta(context.Background(), plugin.Dir, plugin.EntryPoint)
	if err != nil {
		t.Fatalf("load meta: %v", err)
	}
	plugin.Meta = meta
	return plugin
}

func bundleEntry(t *testing.T, relativeEntry string) *Plugin {
	t.Helper()
	bun, err := exec.LookPath("bun")
	if err != nil {
		t.Skip("bun not installed")
	}
	dir := t.TempDir()
	entry, err := filepath.Abs(relativeEntry)
	if err != nil {
		t.Fatal(err)
	}
	command := exec.Command(bun, "build", entry, "--target", "node", "--format", "cjs",
		"--external", "node-html-parser", "--outfile", filepath.Join(dir, "plugin.js"))
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("bundle sample plugin: %v\n%s", err, output)
	}
	return &Plugin{Dir: dir, EntryPoint: "plugin.js", Meta: PluginMeta{Name: filepath.Base(relativeEntry)}}
}

// sampleSite is the website the sample plugin scrapes.
type sampleSite struct {
	*httptest.Server
	searchHits atomic.Int32
}

func newSampleSite(t *testing.T) *sampleSite {
	site := &sampleSite{}
	mux := http.NewServeMux()
	mux.HandleFunc("/search", site.search)
	mux.HandleFunc("/film/maedchen", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `<iframe src="/player/7"></iframe>`)
	})
	mux.HandleFunc("/player/7", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Referer") == "" || r.Header.Get("Accept-Language") == "" {
			http.Error(w, "missing headers", http.StatusBadRequest)
			return
		}
		fmt.Fprint(w, packedPlayer)
	})
	mux.HandleFunc("/video-page", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `<video src="https://cdn.example/clip.mp4"></video>`)
	})
	site.Server = httptest.NewServer(mux)
	t.Cleanup(site.Close)
	return site
}

const searchResults = `<a class="result" data-year="1999" href="/film/other">Das Mädchen Zwei (1999)</a>
<a class="result" data-year="2020" href="/film/maedchen">Das Maedchen (2020)</a>`

func (s *sampleSite) search(w http.ResponseWriter, r *http.Request) {
	hits := s.searchHits.Add(1)
	switch r.URL.Query().Get("q") {
	case "Blocked":
		w.WriteHeader(http.StatusForbidden)
		fmt.Fprint(w, cloudflareChallenge)
	case "Flaky":
		if hits == 1 {
			http.Error(w, "boom", http.StatusInternalServerError)
			return
		}
		fmt.Fprint(w, searchResults)
	case "Das Mädchen":
		fmt.Fprint(w, searchResults)
	default:
		fmt.Fprint(w, "<html></html>")
	}
}

func sampleRunner(site *sampleSite) *Runner {
	runner := NewEmbeddedRunner()
	runner.SetEnv([]string{BaseURLSettingKey + "=" + site.URL})
	return runner
}

func TestSDKv2MetaThroughEmbeddedRuntime(t *testing.T) {
	plugin := bundleSample(t)
	meta, err := NewEmbeddedRunner().loadMeta(context.Background(), plugin.Dir, plugin.EntryPoint)
	if err != nil {
		t.Fatal(err)
	}
	if meta.Name != "sample" || meta.DisplayName != "Sample" || meta.BaseURL != "http://sample.invalid" {
		t.Errorf("unexpected meta: %+v", meta)
	}
	if !meta.Has(CapabilityStreams) || !meta.Has(CapabilityResolve) {
		t.Errorf("capabilities: %v", meta.Capabilities)
	}
	if len(meta.Domains) != 1 || meta.Domains[0] != "*" {
		t.Errorf("domains: %v", meta.Domains)
	}
	if len(meta.Languages) != 1 || meta.Languages[0] != "de" || len(meta.Media) != 2 {
		t.Errorf("languages %v media %v", meta.Languages, meta.Media)
	}
}

// TestSDKv2StreamsThroughEmbeddedRuntime runs ctx.http (headers, base_url
// override), ctx.match (umlauts, year tie-break), ctx.extract (iframes,
// the packer, media URLs) and ctx.list inside goja.
func TestSDKv2StreamsThroughEmbeddedRuntime(t *testing.T) {
	plugin := bundleSample(t)
	site := newSampleSite(t)

	result, err := sampleRunner(site).Streams(context.Background(), plugin, StreamsArgs{
		MediaType: MediaTypeMovie, Title: "Das Mädchen", Year: 2020, TMDBID: 5,
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Downloads) != 1 {
		t.Fatalf("downloads: %+v", result.Downloads)
	}
	download := result.Downloads[0]
	if download.URL != "https://cdn.example/hls/master.m3u8?t=9" {
		t.Errorf("url %q", download.URL)
	}
	if download.Label != "cdn.example" || download.Language != "de" || download.Quality != "1080p" {
		t.Errorf("download %+v", download)
	}
}

func TestSDKv2UnknownTitleIsAnEmptyList(t *testing.T) {
	plugin := bundleSample(t)
	result, err := sampleRunner(newSampleSite(t)).Streams(context.Background(), plugin, StreamsArgs{
		MediaType: MediaTypeMovie, Title: "Nothing here", TMDBID: 1,
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Downloads) != 0 {
		t.Errorf("downloads: %+v", result.Downloads)
	}
}

// TestSDKv2RetriesAfterServerError needs setTimeout for the retry backoff.
func TestSDKv2RetriesAfterServerError(t *testing.T) {
	plugin := bundleSample(t)
	site := newSampleSite(t)

	result, err := sampleRunner(site).Streams(context.Background(), plugin, StreamsArgs{
		MediaType: MediaTypeMovie, Title: "Flaky", Year: 2020, TMDBID: 2,
	})
	// "Flaky" is not the title of any result, so the lookup ends empty: what
	// matters is that the 500 was retried instead of failing the call.
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Downloads) != 0 {
		t.Errorf("downloads: %+v", result.Downloads)
	}
	if hits := site.searchHits.Load(); hits != 2 {
		t.Errorf("search requests: %d, want 2 (one failure, one retry)", hits)
	}
}

func TestSDKv2BlockedErrorCarriesItsCode(t *testing.T) {
	plugin := bundleSample(t)
	site := newSampleSite(t)

	_, err := sampleRunner(site).Streams(context.Background(), plugin, StreamsArgs{
		MediaType: MediaTypeMovie, Title: "Blocked", TMDBID: 3,
	})
	var pluginError *PluginError
	if !errors.As(err, &pluginError) {
		t.Fatalf("want *PluginError, got %v", err)
	}
	if pluginError.Code != ErrorCodeBlocked {
		t.Errorf("code %q, message %q", pluginError.Code, pluginError.Message)
	}
	if got := site.searchHits.Load(); got != 1 {
		t.Errorf("a Cloudflare block must not be retried, got %d requests", got)
	}
}

func TestSDKv2Resolve(t *testing.T) {
	plugin := bundleSample(t)
	site := newSampleSite(t)
	runner := sampleRunner(site)

	result, err := runner.Resolve(context.Background(), plugin, site.URL+"/video-page")
	if err != nil {
		t.Fatal(err)
	}
	if result.URL != "https://cdn.example/clip.mp4" || result.MimeType != "video/mp4" {
		t.Errorf("result %+v", result)
	}

	_, err = runner.Resolve(context.Background(), plugin, site.URL+"/gone")
	var pluginError *PluginError
	if !errors.As(err, &pluginError) || pluginError.Code != ErrorCodeExtractionFailed {
		t.Errorf("want extraction_failed PluginError, got %v", err)
	}
}

func TestPluginErrorFromWireResponse(t *testing.T) {
	plugin := writeBundle(t, `
const { writeSync } = require("fs");
writeSync(1, JSON.stringify({ error: "site down", code: "source_offline" }) + "\n");
process.exit(0);
`)
	_, err := NewEmbeddedRunner().invoke(context.Background(), plugin, "streams", nil)
	var pluginError *PluginError
	if !errors.As(err, &pluginError) {
		t.Fatalf("want *PluginError, got %T %v", err, err)
	}
	if pluginError.Code != ErrorCodeSourceOffline || pluginError.Message != "site down" || pluginError.Plugin != "test" {
		t.Errorf("unexpected error %+v", pluginError)
	}
	if !strings.Contains(err.Error(), `plugin "test": site down`) {
		t.Errorf("message changed: %q", err.Error())
	}
}

func TestPluginErrorWithoutCode(t *testing.T) {
	plugin := writeBundle(t, `
require("fs").writeSync(1, JSON.stringify({ error: "plain" }) + "\n");
process.exit(0);
`)
	_, err := NewEmbeddedRunner().invoke(context.Background(), plugin, "streams", nil)
	var pluginError *PluginError
	if !errors.As(err, &pluginError) || pluginError.Code != "" {
		t.Fatalf("want a PluginError without code, got %v", err)
	}
}

func TestMetaDecodesDefinitionFields(t *testing.T) {
	var meta PluginMeta
	raw := `{"name":"a","version":"1","capabilities":["streams"],"base_url":"https://a.example","languages":["de"],"media":["movie","episode"]}`
	if err := json.Unmarshal([]byte(raw), &meta); err != nil {
		t.Fatal(err)
	}
	if meta.BaseURL != "https://a.example" || meta.Languages[0] != "de" || meta.Media[1] != MediaTypeEpisode {
		t.Errorf("meta %+v", meta)
	}
}

func TestSettingFieldsAddsDomain(t *testing.T) {
	withoutBaseURL := PluginMeta{Settings: []PluginSettingField{{Key: "TOKEN"}}}
	if fields := withoutBaseURL.SettingFields(); len(fields) != 1 {
		t.Errorf("no base_url, no Domain field: %+v", fields)
	}

	meta := PluginMeta{BaseURL: "https://a.example", Settings: []PluginSettingField{{Key: "TOKEN"}}}
	fields := meta.SettingFields()
	if len(fields) != 2 || fields[0].Key != BaseURLSettingKey || fields[0].Label != "Domain" || fields[0].Default != "https://a.example" {
		t.Errorf("fields %+v", fields)
	}
	if len(meta.Settings) != 1 {
		t.Error("SettingFields must not modify the meta")
	}
}

func TestWithoutDefaultBaseURL(t *testing.T) {
	meta := PluginMeta{BaseURL: "https://a.example"}
	cases := map[string]bool{
		"https://a.example":   false,
		"https://a.example/":  false,
		" ":                   false,
		"":                    false,
		"https://mirror.test": true,
	}
	for override, kept := range cases {
		values := meta.WithoutDefaultBaseURL(map[string]string{BaseURLSettingKey: override, "TOKEN": "x"})
		_, present := values[BaseURLSettingKey]
		if present != kept {
			t.Errorf("override %q: kept=%v, want %v", override, present, kept)
		}
		if values["TOKEN"] != "x" {
			t.Errorf("other settings must stay: %v", values)
		}
	}
}

// obfuscatedVoePayload is VOE's current page format for {"source": m3u8}:
// base64, reversed, shifted by 3, base64, rot13.
func obfuscatedVoePayload(source string) string {
	encode := func(text string) string { return base64.StdEncoding.EncodeToString([]byte(text)) }
	reversed := []rune(encode(`{"source":"` + source + `"}`))
	for left, right := 0, len(reversed)-1; left < right; left, right = left+1, right-1 {
		reversed[left], reversed[right] = reversed[right], reversed[left]
	}
	for index := range reversed {
		reversed[index] += 3
	}
	return strings.Map(func(character rune) rune {
		switch {
		case character >= 'a' && character <= 'z':
			return 'a' + (character-'a'+13)%26
		case character >= 'A' && character <= 'Z':
			return 'A' + (character-'A'+13)%26
		}
		return character
	}, encode(string(reversed)))
}

func newHosterSite(t *testing.T) *httptest.Server {
	mux := http.NewServeMux()
	page := func(path, body string) {
		mux.HandleFunc(path, func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, body) })
	}
	page("/d/ID1", "<p>landing</p>")
	page("/e/ID1", `<script>dsplayer.hotkeys = {}; $.get('/pass_md5/abc/def', function(data){ makePlay(data) }); function makePlay(){ return a + "?token=tok123&expiry=" + Date.now(); }</script>`)
	page("/pass_md5/abc/def", "https://storage.example/video/")
	page("/voe/abc", `<script type="application/json">["`+obfuscatedVoePayload("https://d.example/m.m3u8")+`"]</script>`)
	page("/fm/ID", `<div data-hls-url="https://cdn.example/a.m3u8?x=1&amp;y=2"></div>`)
	page("/gen", `<script>var a="https://cdn.example/a.mp4"; var b="https://cdn.example/b.m3u8"</script>`)
	mux.HandleFunc("/e/VID", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, `<script>document.getElementById('robotlink').innerHTML = '//%s/get_video?id=a&token=' + ('xcdXYZ').substring(3);</script>`, r.Host)
	})
	mux.HandleFunc("/get_video", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "https://tapecontent.example/video.mp4", http.StatusFound)
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server
}

// TestSDKv2HostersThroughEmbeddedRuntime runs the hoster extractors (regexes,
// the packer-free decoders, Buffer, manual redirects, URL handling) in goja.
func TestSDKv2HostersThroughEmbeddedRuntime(t *testing.T) {
	plugin := bundleEntry(t, hostersEntry)
	server := newHosterSite(t)
	runner := NewEmbeddedRunner()
	runner.SetEnv([]string{"SITE=" + server.URL})

	raw, err := runner.invoke(context.Background(), plugin, "run", nil)
	if err != nil {
		t.Fatal(err)
	}
	var results map[string]ResolveResult
	if err := json.Unmarshal(raw, &results); err != nil {
		t.Fatal(err)
	}
	expected := map[string]string{
		"streamtape": "https://tapecontent.example/video.mp4",
		"voe":        "https://d.example/m.m3u8",
		"filemoon":   "https://cdn.example/a.m3u8?x=1&y=2",
		"generic":    "https://cdn.example/b.m3u8",
	}
	for name, url := range expected {
		if results[name].URL != url {
			t.Errorf("%s: got %q, want %q", name, results[name].URL, url)
		}
	}
	if !strings.HasPrefix(results["dood"].URL, "https://storage.example/video/") ||
		!strings.Contains(results["dood"].URL, "?token=tok123&expiry=") {
		t.Errorf("dood: %q", results["dood"].URL)
	}
	if results["voe"].MimeType != "application/x-mpegURL" || results["streamtape"].MimeType != "video/mp4" {
		t.Errorf("mime types: %+v", results)
	}
	if results["streamtape"].Headers["Referer"] != server.URL+"/" {
		t.Errorf("referer keeps the port: %+v", results["streamtape"].Headers)
	}
}
