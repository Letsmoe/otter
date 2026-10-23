package together

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"strings"

	"golang.org/x/crypto/scrypt"
)

// 32 symbols without the look-alikes I, O, 0 and 1; a byte modulo 32 is unbiased.
const codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

const codeLength = 8

// Key derivation salt; changing it makes old and new versions unable to meet.
const keySalt = "otter-together-v1"

var ErrInvalidCode = errors.New("invalid room code")

// NewCode returns a random room code like "K7QD9MZA".
func NewCode() (string, error) {
	random := make([]byte, codeLength)
	if _, err := rand.Read(random); err != nil {
		return "", err
	}
	code := make([]byte, codeLength)
	for index, value := range random {
		code[index] = codeAlphabet[int(value)%len(codeAlphabet)]
	}
	return string(code), nil
}

// NormalizeCode accepts what people type or paste ("k7qd-9mza", "K7QD 9MZA").
func NormalizeCode(input string) (string, error) {
	var code strings.Builder
	for _, character := range strings.ToUpper(input) {
		if character == '-' || character == ' ' {
			continue
		}
		if !strings.ContainsRune(codeAlphabet, character) {
			return "", ErrInvalidCode
		}
		code.WriteRune(character)
	}
	if code.Len() != codeLength {
		return "", ErrInvalidCode
	}
	return code.String(), nil
}

// roomKey encrypts signaling for one room. Relays only see the topic and
// ciphertext; the topic is derived from the key, not from the code directly.
type roomKey struct {
	aead  cipher.AEAD
	topic string
}

func deriveRoomKey(code string) (*roomKey, error) {
	// scrypt makes guessing codes from relay traffic expensive.
	secret, err := scrypt.Key([]byte(code), []byte(keySalt), 1<<15, 8, 1, 64)
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(secret[:32])
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	topicHash := sha256.Sum256(secret[32:])
	return &roomKey{aead: aead, topic: hex.EncodeToString(topicHash[:16])}, nil
}

func (key *roomKey) seal(plain []byte) (string, error) {
	nonce := make([]byte, key.aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	sealed := key.aead.Seal(nonce, nonce, plain, nil)
	return base64.StdEncoding.EncodeToString(sealed), nil
}

func (key *roomKey) open(content string) ([]byte, error) {
	sealed, err := base64.StdEncoding.DecodeString(content)
	if err != nil {
		return nil, err
	}
	nonceSize := key.aead.NonceSize()
	if len(sealed) < nonceSize {
		return nil, errors.New("sealed message too short")
	}
	return key.aead.Open(nil, sealed[:nonceSize], sealed[nonceSize:], nil)
}
