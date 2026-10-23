package subtitles

import (
	"bytes"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"unicode/utf16"
	"unicode/utf8"

	"golang.org/x/text/encoding"
	"golang.org/x/text/encoding/charmap"
)

// maxFileBytes bounds a downloaded caption file and what it unpacks to; real
// ones are a few hundred kilobytes.
const maxFileBytes = 4 << 20

var gzipMagic = []byte{0x1f, 0x8b}

var errFileTooLarge = errors.New("subtitle file is too large")

// DecodeText turns a downloaded caption file into UTF-8 text. It unpacks gzip,
// reads UTF-16 and UTF-8 (BOM stripped), and takes any other bytes as the
// legacy code page most files in the language were written in.
func DecodeText(data []byte, language string) (string, error) {
	if bytes.HasPrefix(data, gzipMagic) {
		unpacked, err := gunzip(data)
		if err != nil {
			return "", err
		}
		data = unpacked
	}
	if utf16Text, isUTF16 := decodeUTF16(data); isUTF16 {
		return utf16Text, nil
	}
	data = bytes.TrimPrefix(data, []byte{0xEF, 0xBB, 0xBF})
	if utf8.Valid(data) {
		return string(data), nil
	}
	decoded, err := legacyEncodingFor(language).NewDecoder().Bytes(data)
	if err != nil {
		return "", fmt.Errorf("decode subtitle file: %w", err)
	}
	return string(decoded), nil
}

func gunzip(data []byte) ([]byte, error) {
	reader, err := gzip.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer reader.Close()
	unpacked, err := io.ReadAll(io.LimitReader(reader, maxFileBytes+1))
	if err != nil {
		return nil, err
	}
	if len(unpacked) > maxFileBytes {
		return nil, errFileTooLarge
	}
	return unpacked, nil
}

// decodeUTF16 reads text that starts with a UTF-16 byte order mark.
func decodeUTF16(data []byte) (string, bool) {
	if len(data) < 2 {
		return "", false
	}
	var byteOrderLittle bool
	switch {
	case data[0] == 0xFF && data[1] == 0xFE:
		byteOrderLittle = true
	case data[0] == 0xFE && data[1] == 0xFF:
		byteOrderLittle = false
	default:
		return "", false
	}
	units := make([]uint16, 0, len(data)/2)
	for index := 2; index+1 < len(data); index += 2 {
		if byteOrderLittle {
			units = append(units, uint16(data[index])|uint16(data[index+1])<<8)
			continue
		}
		units = append(units, uint16(data[index])<<8|uint16(data[index+1]))
	}
	return string(utf16.Decode(units)), true
}

// legacyCodePages lists the languages whose files predate UTF-8 in a code
// page other than Windows-1252.
var legacyCodePages = map[string]encoding.Encoding{
	"ru": charmap.Windows1251, "bg": charmap.Windows1251, "uk": charmap.Windows1251,
	"sr": charmap.Windows1251, "mk": charmap.Windows1251,
	"el": charmap.Windows1253,
	"tr": charmap.Windows1254,
	"he": charmap.Windows1255,
	"ar": charmap.Windows1256, "fa": charmap.Windows1256,
	"pl": charmap.Windows1250, "cs": charmap.Windows1250, "sk": charmap.Windows1250,
	"hu": charmap.Windows1250, "ro": charmap.Windows1250, "hr": charmap.Windows1250,
	"sl": charmap.Windows1250, "bs": charmap.Windows1250, "sq": charmap.Windows1250,
	"lt": charmap.Windows1257, "lv": charmap.Windows1257, "et": charmap.Windows1257,
	"vi": charmap.Windows1258,
}

func legacyEncodingFor(language string) encoding.Encoding {
	codePage, known := legacyCodePages[language]
	if known {
		return codePage
	}
	return charmap.Windows1252
}
