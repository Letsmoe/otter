// Package neoworks syncs Otter's profiles, history, lists, ratings and
// settings through Neoworks, using libneoworks (cgo) for the install
// identity, the encrypted local store and the sync loop.
package neoworks

import (
	libneoworks "github.com/neoworks-dev/libneoworks-go"

	"github.com/Letsmoe/otter/internal/secrets"
)

// keystore wraps the install's secrets under Otter's secret key: the Android
// Keystore's key on the phone, the key file in DATA_DIR on a NAS.
type keystore struct {
	box *secrets.Box
}

var _ libneoworks.Keystore = keystore{}

func newKeystore(box *secrets.Box) keystore {
	return keystore{box: box}
}

func (keystore) Name() string {
	return "otter-secret-key"
}

func (store keystore) Wrap(secret, label []byte) ([]byte, error) {
	return store.box.SealBytes(secret, label)
}

func (store keystore) Unwrap(wrapped, label []byte) ([]byte, error) {
	return store.box.OpenBytes(wrapped, label)
}
