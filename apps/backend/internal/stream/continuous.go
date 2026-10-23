package stream

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Continuous streams serve an HLS stream as one file for DLNA TVs, which
// play MPEG-TS and MP4 but rarely HLS. The segments of the best variant are
// fetched (and decrypted) here and written back to back: MPEG-TS segments
// form one valid transport stream, fMP4 fragments after their init segment
// one fragmented MP4. Seeking restarts the stream with ?start=<seconds>.
//
//	GET|HEAD /api/stream/continuous?s=<session>&u=<encodedURL>&start=<seconds>
//
// Responses carry X-Otter-Start (where the stream really starts, a segment
// boundary) and X-Otter-Duration, so the phone can show TV positions on the
// title's own timeline.

type byteRange struct {
	length int64
	offset int64
}

type encryptionKey struct {
	uri string
	// iv is nil when the IV is the segment's media sequence number.
	iv []byte
}

type mediaSegment struct {
	url       string
	duration  float64
	sequence  int64
	byteRange *byteRange
	key       *encryptionKey
}

type mediaPlaylist struct {
	initURL  string
	segments []mediaSegment
	// height is the highest variant resolution a master playlist offers; 0 when unknown.
	height int
}

func (playlist mediaPlaylist) totalDuration() float64 {
	total := 0.0
	for _, segment := range playlist.segments {
		total += segment.duration
	}
	return total
}

// segmentsFrom drops the segments that end before start and returns the
// rest with the time the first of them begins at.
func (playlist mediaPlaylist) segmentsFrom(start float64) ([]mediaSegment, float64) {
	elapsed := 0.0
	for index, segment := range playlist.segments {
		if elapsed+segment.duration > start {
			return playlist.segments[index:], elapsed
		}
		elapsed += segment.duration
	}
	return nil, elapsed
}

var errUnsupportedEncryption = errors.New("unsupported HLS encryption")

// continuousPrefetch is how many segments are fetched ahead in parallel: one
// at a time, every VPN round trip adds up and the TV plays faster than it fills.
const continuousPrefetch = 6

// segmentRetryDelays are the waits before each retry of a failed segment: a
// VPN reconnect or a CDN hiccup must not end the TV's only connection.
var segmentRetryDelays = []time.Duration{time.Second, 2 * time.Second, 4 * time.Second}

func (p *Proxy) serveContinuous(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	manifestURL, err := decURL(query.Get("u"))
	if err != nil {
		http.Error(w, "bad url", http.StatusBadRequest)
		return
	}
	start, _ := strconv.ParseFloat(query.Get("start"), 64)
	headers, _ := p.loadSession(r.Context(), query.Get("s"))

	playlist, err := p.loadMediaPlaylist(r.Context(), manifestURL, headers)
	if err != nil {
		http.Error(w, "playlist: "+err.Error(), http.StatusBadGateway)
		return
	}
	segments, startsAt := playlist.segmentsFrom(start)

	w.Header().Set("Content-Type", continuousContentType(playlist))
	w.Header().Set("X-Otter-Start", strconv.FormatFloat(startsAt, 'f', 3, 64))
	w.Header().Set("X-Otter-Duration", strconv.FormatFloat(playlist.totalDuration(), 'f', 3, 64))
	w.Header().Set("Cache-Control", "no-cache")
	if r.Method == http.MethodHead {
		return
	}
	if err := p.writeContinuous(r.Context(), w, playlist.initURL, segments, headers); err != nil && r.Context().Err() == nil {
		// Headers are out already; ending the body early tells the TV.
		log.Printf("continuous stream: %v", err)
	}
}

func continuousContentType(playlist mediaPlaylist) string {
	if playlist.initURL != "" {
		return "video/mp4"
	}
	// What DLNA renderers expect for MPEG-TS.
	return "video/mpeg"
}

func (p *Proxy) writeContinuous(ctx context.Context, w http.ResponseWriter, initURL string, segments []mediaSegment, headers map[string]string) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	keys := &keyCache{keys: map[string][]byte{}}
	if initURL != "" {
		segments = append([]mediaSegment{{url: initURL}}, segments...)
	}
	for pending := range p.prefetchSegments(ctx, segments, headers, keys) {
		result := <-pending
		if result.err != nil {
			return result.err
		}
		if _, err := w.Write(result.body); err != nil {
			return err
		}
		if flusher, ok := w.(http.Flusher); ok {
			flusher.Flush()
		}
	}
	return ctx.Err()
}

type fetchedSegment struct {
	body []byte
	err  error
}

// prefetchSegments fetches up to continuousPrefetch segments at once and
// hands out their results in playlist order.
func (p *Proxy) prefetchSegments(ctx context.Context, segments []mediaSegment, headers map[string]string, keys *keyCache) <-chan chan fetchedSegment {
	ordered := make(chan chan fetchedSegment, continuousPrefetch)
	go func() {
		defer close(ordered)
		for _, segment := range segments {
			result := make(chan fetchedSegment, 1)
			select {
			case ordered <- result:
			case <-ctx.Done():
				return
			}
			go func(segment mediaSegment) {
				body, err := p.segmentBody(ctx, segment, headers, keys)
				result <- fetchedSegment{body: body, err: err}
			}(segment)
		}
	}()
	return ordered
}

func (p *Proxy) segmentBody(ctx context.Context, segment mediaSegment, headers map[string]string, keys *keyCache) ([]byte, error) {
	body, err := p.readSegment(ctx, segment, headers)
	if err != nil {
		return nil, fmt.Errorf("segment %s: %w", segment.url, err)
	}
	if segment.key == nil {
		return body, nil
	}
	body, err = p.decryptSegment(ctx, body, segment, headers, keys)
	if err != nil {
		return nil, fmt.Errorf("decrypt %s: %w", segment.url, err)
	}
	return body, nil
}

// keyCache holds the AES keys of one stream; segments are fetched in parallel.
type keyCache struct {
	mutex sync.Mutex
	keys  map[string][]byte
}

// readSegment prefers the local copy: a cached segment or a downloaded blob.
func (p *Proxy) readSegment(ctx context.Context, segment mediaSegment, headers map[string]string) ([]byte, error) {
	if segment.byteRange == nil {
		if local, ok := p.readLocalBlob(ctx, segment.url); ok {
			return local, nil
		}
		if cached, ok := p.readCachedSegment(ctx, segment.url); ok {
			return cached, nil
		}
	}
	rangeHeaders := headers
	if segment.byteRange != nil {
		rangeHeaders = withRange(headers, segment.byteRange)
	}
	return p.fetchWithRetries(ctx, segment.url, rangeHeaders)
}

func (p *Proxy) fetchWithRetries(ctx context.Context, rawURL string, headers map[string]string) ([]byte, error) {
	body, err := p.fetchBody(ctx, rawURL, headers)
	for _, delay := range segmentRetryDelays {
		if err == nil || ctx.Err() != nil {
			break
		}
		log.Printf("continuous stream: retrying in %v: %v", delay, err)
		select {
		case <-time.After(delay):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
		body, err = p.fetchBody(ctx, rawURL, headers)
	}
	return body, err
}

func (p *Proxy) fetchBody(ctx context.Context, rawURL string, headers map[string]string) ([]byte, error) {
	response, err := p.cache.fetch(ctx, rawURL, headers)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	return io.ReadAll(response.Body)
}

func withRange(headers map[string]string, segmentRange *byteRange) map[string]string {
	combined := make(map[string]string, len(headers)+1)
	for name, value := range headers {
		combined[name] = value
	}
	combined["Range"] = fmt.Sprintf("bytes=%d-%d", segmentRange.offset, segmentRange.offset+segmentRange.length-1)
	return combined
}

func (p *Proxy) readCachedSegment(ctx context.Context, segmentURL string) ([]byte, bool) {
	hash := urlHash(segmentURL)
	if value, _ := p.cache.kv.Get(ctx, kvSegmentPrefix+hash); value != "1" {
		return nil, false
	}
	return p.readBlob(ctx, blobPrefix+"seg/"+hash+segExt(segmentURL))
}

// readLocalBlob reads URLs that point at this backend's own blob store, e.g.
// the segments of a downloaded title.
func (p *Proxy) readLocalBlob(ctx context.Context, rawURL string) ([]byte, bool) {
	key, ok := p.localBlobKey(rawURL)
	if !ok {
		return nil, false
	}
	return p.readBlob(ctx, key)
}

func (p *Proxy) readBlob(ctx context.Context, key string) ([]byte, bool) {
	reader, err := p.cache.storage.Get(ctx, key)
	if err != nil {
		return nil, false
	}
	defer reader.Close()
	body, err := io.ReadAll(reader)
	if err != nil {
		return nil, false
	}
	return body, true
}

func (p *Proxy) decryptSegment(ctx context.Context, body []byte, segment mediaSegment, headers map[string]string, keys *keyCache) ([]byte, error) {
	key, err := p.segmentKeyBytes(ctx, segment.key.uri, headers, keys)
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	if len(body)%aes.BlockSize != 0 {
		return nil, errors.New("ciphertext is not a whole number of blocks")
	}
	iv := segment.key.iv
	if iv == nil {
		iv = sequenceIV(segment.sequence)
	}
	plain := make([]byte, len(body))
	cipher.NewCBCDecrypter(block, iv).CryptBlocks(plain, body)
	return withoutPadding(plain), nil
}

// segmentKeyBytes fetches each key once; the lock makes parallel segments wait for it.
func (p *Proxy) segmentKeyBytes(ctx context.Context, keyURL string, headers map[string]string, keys *keyCache) ([]byte, error) {
	keys.mutex.Lock()
	defer keys.mutex.Unlock()
	if key, ok := keys.keys[keyURL]; ok {
		return key, nil
	}
	key, ok := p.readLocalBlob(ctx, keyURL)
	if !ok {
		response, err := p.cache.fetch(ctx, keyURL, headers)
		if err != nil {
			return nil, err
		}
		defer response.Body.Close()
		key, err = io.ReadAll(response.Body)
		if err != nil {
			return nil, err
		}
	}
	if len(key) != aes.BlockSize {
		return nil, fmt.Errorf("key is %d bytes, want %d", len(key), aes.BlockSize)
	}
	keys.keys[keyURL] = key
	return key, nil
}

func sequenceIV(sequence int64) []byte {
	iv := make([]byte, aes.BlockSize)
	binary.BigEndian.PutUint64(iv[8:], uint64(sequence))
	return iv
}

// withoutPadding strips PKCS#7 padding; malformed padding is left as is.
func withoutPadding(plain []byte) []byte {
	if len(plain) == 0 {
		return plain
	}
	padding := int(plain[len(plain)-1])
	if padding == 0 || padding > aes.BlockSize || padding > len(plain) {
		return plain
	}
	if !bytes.Equal(plain[len(plain)-padding:], bytes.Repeat([]byte{byte(padding)}, padding)) {
		return plain
	}
	return plain[:len(plain)-padding]
}

// -- playlists --

// loadMediaPlaylist follows a master playlist to its best variant.
func (p *Proxy) loadMediaPlaylist(ctx context.Context, manifestURL string, headers map[string]string) (mediaPlaylist, error) {
	content, err := p.readPlaylist(ctx, manifestURL, headers)
	if err != nil {
		return mediaPlaylist{}, err
	}
	base, _ := url.Parse(manifestURL)
	height := highestResolution(content)
	variantURL := continuousVariant(content, base)
	if variantURL != "" {
		base, _ = url.Parse(variantURL)
		content, err = p.readPlaylist(ctx, variantURL, headers)
		if err != nil {
			return mediaPlaylist{}, err
		}
	}
	playlist, err := parseMediaPlaylist(content, base)
	playlist.height = height
	return playlist, err
}

// highestResolution reads the tallest RESOLUTION=WxH of a master playlist's
// variants; 0 for media playlists and masters that don't say.
func highestResolution(manifest string) int {
	highest := 0
	for _, rawLine := range strings.Split(manifest, "\n") {
		line := strings.TrimSpace(rawLine)
		if !strings.HasPrefix(line, "#EXT-X-STREAM-INF") {
			continue
		}
		_, heightText, found := strings.Cut(attributeValue(line, "RESOLUTION"), "x")
		height, err := strconv.Atoi(heightText)
		if found && err == nil && height > highest {
			highest = height
		}
	}
	return highest
}

func (p *Proxy) readPlaylist(ctx context.Context, playlistURL string, headers map[string]string) (string, error) {
	if local, ok := p.readLocalBlob(ctx, playlistURL); ok {
		return string(local), nil
	}
	response, err := p.cache.fetch(ctx, playlistURL, headers)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	return string(body), err
}

// continuousVariant picks the highest-bandwidth variant that carries its own
// audio: renditions in a separate audio playlist can't be joined in here.
// "" for a media playlist.
func continuousVariant(manifest string, base *url.URL) string {
	if !strings.Contains(manifest, "#EXT-X-STREAM-INF") {
		return ""
	}
	best := highestBandwidthVariant(withoutSeparateAudio(manifest), base)
	if best != "" {
		return best
	}
	return highestBandwidthVariant(manifest, base)
}

// withoutSeparateAudio drops the variants that reference an AUDIO group.
func withoutSeparateAudio(manifest string) string {
	var kept []string
	skipNext := false
	for _, rawLine := range strings.Split(manifest, "\n") {
		line := strings.TrimSpace(rawLine)
		if strings.HasPrefix(line, "#EXT-X-STREAM-INF") && strings.Contains(line, "AUDIO=") {
			skipNext = true
			continue
		}
		if skipNext && line != "" && !strings.HasPrefix(line, "#") {
			skipNext = false
			continue
		}
		kept = append(kept, line)
	}
	return strings.Join(kept, "\n")
}

// parseMediaPlaylist reads the segments of a VOD media playlist with their
// durations, byte ranges and AES-128 keys.
func parseMediaPlaylist(content string, base *url.URL) (mediaPlaylist, error) {
	parser := playlistParser{base: base}
	for _, rawLine := range strings.Split(content, "\n") {
		if err := parser.readLine(strings.TrimSpace(rawLine)); err != nil {
			return mediaPlaylist{}, err
		}
	}
	if len(parser.playlist.segments) == 0 {
		return mediaPlaylist{}, errors.New("no segments")
	}
	return parser.playlist, nil
}

type playlistParser struct {
	base         *url.URL
	playlist     mediaPlaylist
	sequence     int64
	duration     float64
	pendingRange *byteRange
	key          *encryptionKey
	// nextOffset is where an EXT-X-BYTERANGE without @offset continues.
	nextOffset int64
}

func (parser *playlistParser) readLine(line string) error {
	switch {
	case line == "":
		return nil
	case strings.HasPrefix(line, "#EXT-X-MEDIA-SEQUENCE:"):
		parser.sequence, _ = strconv.ParseInt(strings.TrimPrefix(line, "#EXT-X-MEDIA-SEQUENCE:"), 10, 64)
	case strings.HasPrefix(line, "#EXTINF:"):
		parser.duration = extinfDuration(line)
	case strings.HasPrefix(line, "#EXT-X-BYTERANGE:"):
		parser.pendingRange = parseByteRange(strings.TrimPrefix(line, "#EXT-X-BYTERANGE:"), parser.nextOffset)
	case strings.HasPrefix(line, "#EXT-X-KEY:"):
		return parser.readKey(line)
	case strings.HasPrefix(line, "#EXT-X-MAP:"):
		parser.playlist.initURL = absURL(parser.base, attributeValue(line, "URI"))
	case strings.HasPrefix(line, "#"):
		return nil
	default:
		parser.addSegment(line)
	}
	return nil
}

func (parser *playlistParser) readKey(line string) error {
	method := attributeValue(line, "METHOD")
	if method == "NONE" {
		parser.key = nil
		return nil
	}
	if method != "AES-128" {
		return fmt.Errorf("%w: %s", errUnsupportedEncryption, method)
	}
	key := &encryptionKey{uri: absURL(parser.base, attributeValue(line, "URI"))}
	if ivText := attributeValue(line, "IV"); ivText != "" {
		iv, err := hex.DecodeString(strings.TrimPrefix(strings.TrimPrefix(ivText, "0x"), "0X"))
		if err != nil || len(iv) != aes.BlockSize {
			return fmt.Errorf("bad IV %q", ivText)
		}
		key.iv = iv
	}
	parser.key = key
	return nil
}

func (parser *playlistParser) addSegment(line string) {
	segment := mediaSegment{
		url:       absURL(parser.base, line),
		duration:  parser.duration,
		sequence:  parser.sequence,
		byteRange: parser.pendingRange,
		key:       parser.key,
	}
	parser.playlist.segments = append(parser.playlist.segments, segment)
	if parser.pendingRange != nil {
		parser.nextOffset = parser.pendingRange.offset + parser.pendingRange.length
	}
	parser.sequence++
	parser.duration = 0
	parser.pendingRange = nil
}

func extinfDuration(line string) float64 {
	value := strings.TrimPrefix(line, "#EXTINF:")
	value, _, _ = strings.Cut(value, ",")
	duration, _ := strconv.ParseFloat(strings.TrimSpace(value), 64)
	return duration
}

// parseByteRange reads "<length>[@<offset>]".
func parseByteRange(value string, continuedOffset int64) *byteRange {
	lengthText, offsetText, hasOffset := strings.Cut(value, "@")
	length, err := strconv.ParseInt(strings.TrimSpace(lengthText), 10, 64)
	if err != nil {
		return nil
	}
	offset := continuedOffset
	if hasOffset {
		offset, _ = strconv.ParseInt(strings.TrimSpace(offsetText), 10, 64)
	}
	return &byteRange{length: length, offset: offset}
}

// attributeValue reads NAME=value or NAME="value" from an HLS tag.
func attributeValue(line string, name string) string {
	_, attributes, _ := strings.Cut(line, ":")
	for _, attribute := range splitAttributes(attributes) {
		key, value, found := strings.Cut(attribute, "=")
		if found && strings.TrimSpace(key) == name {
			return strings.Trim(strings.TrimSpace(value), `"`)
		}
	}
	return ""
}

// splitAttributes splits on commas outside quoted values.
func splitAttributes(attributes string) []string {
	var parts []string
	var current strings.Builder
	quoted := false
	for _, character := range attributes {
		if character == '"' {
			quoted = !quoted
		}
		if character == ',' && !quoted {
			parts = append(parts, current.String())
			current.Reset()
			continue
		}
		current.WriteRune(character)
	}
	return append(parts, current.String())
}
