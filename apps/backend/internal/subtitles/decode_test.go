package subtitles

import (
	"bytes"
	"compress/gzip"
	"testing"
)

func gzipped(t *testing.T, content []byte) []byte {
	t.Helper()
	var buffer bytes.Buffer
	writer := gzip.NewWriter(&buffer)
	if _, err := writer.Write(content); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func TestDecodeTextStripsBOMFromUTF8(t *testing.T) {
	text, err := DecodeText([]byte("\xEF\xBB\xBF1\nÜber"), "de")
	if err != nil || text != "1\nÜber" {
		t.Errorf("got %q, %v", text, err)
	}
}

func TestDecodeTextReadsWindows1252(t *testing.T) {
	text, err := DecodeText([]byte("Gr\xfc\xdfe \x93quoted\x94"), "de")
	if err != nil || text != "Grüße “quoted”" {
		t.Errorf("got %q, %v", text, err)
	}
}

func TestDecodeTextPicksTheCodePageOfTheLanguage(t *testing.T) {
	text, err := DecodeText([]byte("\xcf\xf0\xe8\xe2\xe5\xf2"), "ru")
	if err != nil || text != "Привет" {
		t.Errorf("windows-1251: got %q, %v", text, err)
	}
	text, err = DecodeText([]byte("\xb3\xf3\xbf"), "pl")
	if err != nil || text != "łóż" {
		t.Errorf("windows-1250: got %q, %v", text, err)
	}
}

func TestDecodeTextUnpacksGzip(t *testing.T) {
	packed := gzipped(t, []byte("\xEF\xBB\xBFgrüße"))
	text, err := DecodeText(packed, "de")
	if err != nil || text != "grüße" {
		t.Errorf("got %q, %v", text, err)
	}
}

func TestDecodeTextReadsUTF16WithBOM(t *testing.T) {
	little := []byte{0xFF, 0xFE, 'h', 0, 0xFC, 0, 'i', 0}
	text, err := DecodeText(little, "de")
	if err != nil || text != "hüi" {
		t.Errorf("utf-16le: got %q, %v", text, err)
	}
	big := []byte{0xFE, 0xFF, 0, 'h', 0, 0xFC, 0, 'i'}
	text, err = DecodeText(big, "de")
	if err != nil || text != "hüi" {
		t.Errorf("utf-16be: got %q, %v", text, err)
	}
}

func TestDecodeTextRefusesGzipBombs(t *testing.T) {
	bomb := gzipped(t, bytes.Repeat([]byte("a"), maxFileBytes+10))
	if _, err := DecodeText(bomb, "en"); err == nil {
		t.Error("expected an error for an oversized file")
	}
}
