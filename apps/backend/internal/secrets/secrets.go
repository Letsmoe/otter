// Package secrets seals values (plugin passwords and sessions) with
// AES-256-GCM before they are stored. The key comes from SECRET_KEY (hex; the
// Android app passes one kept in the Android Keystore) or else a key file in
// DATA_DIR, created on first start and readable only by its owner.
package secrets

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

const keyBytes = 32

const keyFileName = "secret.key"

// Box seals and opens values with one key.
type Box struct {
	aead cipher.AEAD
}

// Load returns the box for SECRET_KEY, else for the key file in dataDir.
func Load(dataDir string) (*Box, error) {
	key, err := loadKey(dataDir)
	if err != nil {
		return nil, err
	}
	return New(key)
}

// New makes a box for a 32-byte key.
func New(key []byte) (*Box, error) {
	if len(key) != keyBytes {
		return nil, fmt.Errorf("secret key: %d bytes, want %d", len(key), keyBytes)
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return &Box{aead: aead}, nil
}

func loadKey(dataDir string) ([]byte, error) {
	configured := strings.TrimSpace(os.Getenv("SECRET_KEY"))
	if configured != "" {
		return hex.DecodeString(configured)
	}
	return keyFromFile(filepath.Join(dataDir, keyFileName))
}

// keyFromFile reads the key file, creating it with a random key the first time.
func keyFromFile(path string) ([]byte, error) {
	stored, err := os.ReadFile(path)
	if err == nil {
		return hex.DecodeString(strings.TrimSpace(string(stored)))
	}
	if !errors.Is(err, os.ErrNotExist) {
		return nil, fmt.Errorf("secret key: %w", err)
	}
	key := make([]byte, keyBytes)
	if _, err := rand.Read(key); err != nil {
		return nil, err
	}
	if err := os.WriteFile(path, []byte(hex.EncodeToString(key)), 0o600); err != nil {
		return nil, fmt.Errorf("secret key: %w", err)
	}
	return key, nil
}

// Seal encrypts plaintext; the result is base64 text with the nonce in front.
func (box *Box) Seal(plaintext string) (string, error) {
	nonce := make([]byte, box.aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	sealed := box.aead.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.StdEncoding.EncodeToString(sealed), nil
}

// Open decrypts what Seal returned; a changed key or value is an error.
func (box *Box) Open(sealed string) (string, error) {
	if sealed == "" {
		return "", nil
	}
	data, err := base64.StdEncoding.DecodeString(sealed)
	if err != nil {
		return "", fmt.Errorf("open secret: %w", err)
	}
	nonceSize := box.aead.NonceSize()
	if len(data) < nonceSize {
		return "", errors.New("open secret: too short")
	}
	plaintext, err := box.aead.Open(nil, data[:nonceSize], data[nonceSize:], nil)
	if err != nil {
		return "", fmt.Errorf("open secret: %w", err)
	}
	return string(plaintext), nil
}
