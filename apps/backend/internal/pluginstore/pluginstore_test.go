package pluginstore

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha512"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

const testPackage = "@neoworks-dev/otter-plugin-kinoking"

// fakeRegistry serves one package's latest release and its tarball.
type fakeRegistry struct {
	server    *httptest.Server
	version   string
	tarball   []byte
	integrity string
	downloads int
}

func newFakeRegistry(t *testing.T, version string) *fakeRegistry {
	t.Helper()
	registry := &fakeRegistry{version: version}
	registry.tarball = buildTarball(t, map[string]string{
		"package/plugin.js":    "module.exports = 'v" + version + "'",
		"package/package.json": `{"name":"` + testPackage + `","version":"` + version + `","main":"plugin.js"}`,
		"package/README.md":    "ignored",
	})
	digest := sha512.Sum512(registry.tarball)
	registry.integrity = "sha512-" + base64.StdEncoding.EncodeToString(digest[:])
	registry.server = httptest.NewServer(http.HandlerFunc(registry.serve))
	t.Cleanup(registry.server.Close)
	return registry
}

func (r *fakeRegistry) serve(writer http.ResponseWriter, request *http.Request) {
	if request.URL.EscapedPath() == "/@neoworks-dev%2Fotter-plugin-kinoking/latest" {
		answer := map[string]any{
			"name":    testPackage,
			"version": r.version,
			"dist":    map[string]string{"tarball": r.server.URL + "/tarball.tgz", "integrity": r.integrity},
		}
		json.NewEncoder(writer).Encode(answer)
		return
	}
	if request.URL.Path == "/tarball.tgz" {
		r.downloads++
		writer.Write(r.tarball)
		return
	}
	http.NotFound(writer, request)
}

func buildTarball(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buffer bytes.Buffer
	compressed := gzip.NewWriter(&buffer)
	archive := tar.NewWriter(compressed)
	for name, content := range files {
		archive.WriteHeader(&tar.Header{Name: name, Mode: 0o644, Size: int64(len(content)), Typeflag: tar.TypeReg})
		archive.Write([]byte(content))
	}
	archive.Close()
	compressed.Close()
	return buffer.Bytes()
}

func installPlugin(t *testing.T, pluginDir string, manifest string) string {
	t.Helper()
	folder := filepath.Join(pluginDir, "kinoking")
	os.MkdirAll(folder, 0o755)
	os.WriteFile(filepath.Join(folder, "plugin.js"), []byte("module.exports = 'old'"), 0o644)
	os.WriteFile(filepath.Join(folder, "package.json"), []byte(manifest), 0o644)
	return folder
}

func newTestService(pluginDir string, registry *fakeRegistry) *Service {
	service := New(pluginDir)
	service.registryURL = registry.server.URL
	return service
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func TestUpdatesToNewerRelease(t *testing.T) {
	registry := newFakeRegistry(t, "0.2.0")
	pluginDir := t.TempDir()
	folder := installPlugin(t, pluginDir, `{"name":"`+testPackage+`","version":"0.1.0","main":"plugin.js"}`)

	newTestService(pluginDir, registry).UpdateAll(context.Background())

	if got := readFile(t, filepath.Join(folder, "plugin.js")); got != "module.exports = 'v0.2.0'" {
		t.Fatalf("plugin.js = %q", got)
	}
	installed, _, _ := readInstalledPackage(folder)
	if installed.Version != "0.2.0" {
		t.Fatalf("installed version = %q", installed.Version)
	}
	if _, err := os.Stat(filepath.Join(folder, "README.md")); !os.IsNotExist(err) {
		t.Fatal("files outside the bundle were installed")
	}
}

func TestNeverDowngradesOrReinstalls(t *testing.T) {
	for _, installedVersion := range []string{"0.2.0", "0.3.0"} {
		registry := newFakeRegistry(t, "0.2.0")
		pluginDir := t.TempDir()
		folder := installPlugin(t, pluginDir, `{"name":"`+testPackage+`","version":"`+installedVersion+`","main":"plugin.js"}`)

		newTestService(pluginDir, registry).UpdateAll(context.Background())

		if registry.downloads != 0 || readFile(t, filepath.Join(folder, "plugin.js")) != "module.exports = 'old'" {
			t.Fatalf("installed %s was replaced by 0.2.0", installedVersion)
		}
	}
}

func TestSkipsLocalBuilds(t *testing.T) {
	registry := newFakeRegistry(t, "9.0.0")
	pluginDir := t.TempDir()
	folder := installPlugin(t, pluginDir, `{"name":"`+testPackage+`","version":"0.1.0","main":"plugin.js","localBuild":true}`)

	newTestService(pluginDir, registry).UpdateAll(context.Background())

	if registry.downloads != 0 || readFile(t, filepath.Join(folder, "plugin.js")) != "module.exports = 'old'" {
		t.Fatal("a local build was replaced")
	}
}

func TestRejectsIntegrityMismatch(t *testing.T) {
	registry := newFakeRegistry(t, "0.2.0")
	registry.integrity = "sha512-" + base64.StdEncoding.EncodeToString(make([]byte, 64))
	pluginDir := t.TempDir()
	folder := installPlugin(t, pluginDir, `{"name":"`+testPackage+`","version":"0.1.0","main":"plugin.js"}`)

	newTestService(pluginDir, registry).UpdateAll(context.Background())

	if readFile(t, filepath.Join(folder, "plugin.js")) != "module.exports = 'old'" {
		t.Fatal("a tarball with the wrong integrity was installed")
	}
}

func TestCompareVersions(t *testing.T) {
	cases := []struct {
		left, right string
		want        int
	}{
		{"1.0.0", "1.0.0", 0},
		{"1.0.1", "1.0.0", 1},
		{"1.10.0", "1.9.0", 1},
		{"2.0.0", "10.0.0", -1},
		{"1.0.0", "1.0.0-beta.1", 1},
		{"1.0.0-beta.2", "1.0.0-beta.1", 1},
		{"v1.2.0", "1.2", 0},
	}
	for _, testCase := range cases {
		if got := compareVersions(testCase.left, testCase.right); got != testCase.want {
			t.Errorf("compareVersions(%q, %q) = %d, want %d", testCase.left, testCase.right, got, testCase.want)
		}
	}
}

func TestFolderName(t *testing.T) {
	if got := FolderName("@neoworks-dev/otter-plugin-eporner"); got != "eporner" {
		t.Fatalf("FolderName = %q", got)
	}
	if got := FolderName("custom-plugin"); got != "custom-plugin" {
		t.Fatalf("FolderName = %q", got)
	}
}
