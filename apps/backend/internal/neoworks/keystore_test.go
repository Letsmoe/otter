package neoworks

import (
	"bytes"
	"testing"

	libneoworks "github.com/neoworks-dev/libneoworks-go"

	"github.com/Letsmoe/otter/internal/secrets"
)

func testKeystore(t *testing.T) keystore {
	t.Helper()
	box, err := secrets.New(bytes.Repeat([]byte{7}, 32))
	if err != nil {
		t.Fatal(err)
	}
	return newKeystore(box)
}

func TestAWrappedSecretOpensOnlyUnderItsLabel(t *testing.T) {
	store := testKeystore(t)
	wrapped, err := store.Wrap([]byte("install secret"), []byte("install"))
	if err != nil {
		t.Fatal(err)
	}
	unwrapped, err := store.Unwrap(wrapped, []byte("install"))
	if err != nil || string(unwrapped) != "install secret" {
		t.Fatalf("Unwrap = %q, %v", unwrapped, err)
	}
	if _, err := store.Unwrap(wrapped, []byte("other")); err == nil {
		t.Fatal("a secret opened under another label")
	}
}

// The install identity survives a restart: sealed with the keystore, opened again.
func TestTheInstallReopensFromItsSealedForm(t *testing.T) {
	store := testKeystore(t)
	install, err := libneoworks.NewInstall()
	if err != nil {
		t.Fatal(err)
	}
	defer install.Close()
	created, err := install.Describe()
	if err != nil {
		t.Fatal(err)
	}
	sealed, err := install.Seal(store)
	if err != nil {
		t.Fatal(err)
	}
	reopened, err := libneoworks.OpenInstall(sealed, store)
	if err != nil {
		t.Fatal(err)
	}
	defer reopened.Close()
	restored, err := reopened.Describe()
	if err != nil {
		t.Fatal(err)
	}
	if restored.InstallID != created.InstallID {
		t.Fatalf("reopened install %q, want %q", restored.InstallID, created.InstallID)
	}
}
