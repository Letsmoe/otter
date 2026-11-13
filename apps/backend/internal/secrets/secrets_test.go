package secrets

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSealedValuesOpenWithTheSameKeyOnly(t *testing.T) {
	t.Setenv("SECRET_KEY", "")
	dataDir := t.TempDir()
	box, err := Load(dataDir)
	if err != nil {
		t.Fatal(err)
	}
	sealed, err := box.Seal("hunter2")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(sealed, "hunter2") {
		t.Fatal("the sealed value contains the plaintext")
	}

	reloaded, err := Load(dataDir)
	if err != nil {
		t.Fatal(err)
	}
	opened, err := reloaded.Open(sealed)
	if err != nil || opened != "hunter2" {
		t.Fatalf("opened %q, %v; want the password with the saved key", opened, err)
	}

	other, _ := Load(t.TempDir())
	if _, err := other.Open(sealed); err == nil {
		t.Fatal("another key opened the value")
	}
}

func TestKeyFileIsPrivate(t *testing.T) {
	t.Setenv("SECRET_KEY", "")
	dataDir := t.TempDir()
	if _, err := Load(dataDir); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(filepath.Join(dataDir, keyFileName))
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("key file mode %v, want 0600", info.Mode().Perm())
	}
}

func TestSecretKeyEnvironmentWinsOverTheKeyFile(t *testing.T) {
	t.Setenv("SECRET_KEY", strings.Repeat("ab", keyBytes))
	dataDir := t.TempDir()
	if _, err := Load(dataDir); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dataDir, keyFileName)); !os.IsNotExist(err) {
		t.Fatal("a key file was written although SECRET_KEY is set")
	}
}

func TestTamperedValuesDoNotOpen(t *testing.T) {
	box, _ := New([]byte(strings.Repeat("k", keyBytes)))
	sealed, _ := box.Seal("session")
	tampered := sealed[:len(sealed)-4] + "AAA="
	if _, err := box.Open(tampered); err == nil {
		t.Fatal("a changed value opened")
	}
}
