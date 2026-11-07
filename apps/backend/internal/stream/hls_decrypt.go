package stream

import (
	"crypto/aes"
	"crypto/cipher"
	"errors"
)

// decryptAES128 decrypts one AES-128 (CBC) HLS segment and strips its padding.
func decryptAES128(body, key, iv []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	if len(body)%aes.BlockSize != 0 {
		return nil, errors.New("ciphertext is not a whole number of blocks")
	}
	plain := make([]byte, len(body))
	cipher.NewCBCDecrypter(block, iv).CryptBlocks(plain, body)
	return withoutPadding(plain), nil
}

// segmentIV is the segment's explicit IV, else its media sequence number.
func segmentIV(segment mediaSegment) []byte {
	if segment.key.iv != nil {
		return segment.key.iv
	}
	return sequenceIV(segment.sequence)
}
