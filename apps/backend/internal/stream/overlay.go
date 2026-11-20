package stream

import (
	"bufio"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strings"

	"github.com/Letsmoe/otter/internal/errorreport"
)

// Overlay streams carry the picture the phone draws for DLNA TVs: captions,
// "Skip intro on your phone" and the next episode's card are part of the
// video, so TVs that ignore every caption format show them too. The Android
// app re-encodes the video with them (Media3, hardware encoder) and serves
// fragmented MP4 on loopback; it reaches the TV here as MPEG-TS, the way
// continuous streams do. Seeking restarts the stream, so it is never ranged.
//
//	GET|HEAD /api/stream/overlay?job=<id>

const overlayContentType = "video/mpeg"

// A box larger than this is not a video fragment the phone wrote.
const maxOverlayBoxBytes = 256 << 20

var overlayJobPattern = regexp.MustCompile(`^[A-Za-z0-9-]{1,64}$`)

var errNoOverlaySource = errors.New("no overlay encoder on this device")

// overlayClient reads from the phone's own encoder on loopback, for as long as the TV watches.
var overlayClient = &http.Client{}

// SetOverlaySource names the app's loopback server that encodes overlay
// streams, e.g. "http://127.0.0.1:47834"; empty where there is none (a NAS).
func (p *Proxy) SetOverlaySource(baseURL string) {
	p.overlaySource = strings.TrimRight(baseURL, "/")
}

func (p *Proxy) serveOverlay(w http.ResponseWriter, r *http.Request) {
	if p.overlaySource == "" {
		http.Error(w, errNoOverlaySource.Error(), http.StatusNotFound)
		return
	}
	job := r.URL.Query().Get("job")
	if !overlayJobPattern.MatchString(job) {
		http.Error(w, "bad job", http.StatusBadRequest)
		return
	}
	w.Header().Set("Content-Type", overlayContentType)
	// Some TVs give up on a HEAD that takes longer than a second: it is answered without the encoder.
	if r.Method == http.MethodHead {
		w.WriteHeader(http.StatusOK)
		return
	}
	err := p.relayOverlay(r.Context(), w, job)
	if err == nil || r.Context().Err() != nil {
		return
	}
	log.Printf("overlay stream: %v", err)
	errorreport.Capture(r.Context(), err, "stream.overlay", map[string]any{"job": job, "user_agent": r.UserAgent()})
}

// relayOverlay reads the encoder's fragmented MP4 and writes it to the TV as MPEG-TS.
func (p *Proxy) relayOverlay(ctx context.Context, w http.ResponseWriter, job string) error {
	body, err := p.openOverlay(ctx, job)
	if err != nil {
		http.Error(w, "overlay: "+err.Error(), http.StatusBadGateway)
		return err
	}
	defer body.Close()
	boxes := bufio.NewReaderSize(body, 1<<20)
	initSegment, err := readOverlayInit(boxes)
	if err != nil {
		http.Error(w, "overlay: "+err.Error(), http.StatusBadGateway)
		return err
	}
	fragments, err := newOverlayFragments(initSegment)
	if err != nil {
		http.Error(w, "overlay: "+err.Error(), http.StatusBadGateway)
		return err
	}
	remuxer, err := newTSRemuxer(w, initSegment)
	if err != nil {
		http.Error(w, "overlay: "+err.Error(), http.StatusBadGateway)
		return err
	}
	return relayOverlayFragments(w, boxes, fragments, remuxer)
}

func (p *Proxy) openOverlay(ctx context.Context, job string) (io.ReadCloser, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, p.overlaySource+"/overlay/"+url.PathEscape(job), nil)
	if err != nil {
		return nil, err
	}
	response, err := overlayClient.Do(request)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		response.Body.Close()
		return nil, fmt.Errorf("encoder answered HTTP %d", response.StatusCode)
	}
	return response.Body, nil
}

func relayOverlayFragments(w http.ResponseWriter, boxes io.Reader, fragments *overlayFragments, remuxer *tsRemuxer) error {
	for {
		fragment, err := readOverlayFragment(boxes)
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}
		parts, err := fragments.parts(fragment)
		if err != nil {
			return fmt.Errorf("fMP4 fragment: %w", err)
		}
		if err := remuxer.writeParts(parts); err != nil {
			return err
		}
		if flusher, ok := w.(http.Flusher); ok {
			flusher.Flush()
		}
	}
}

// readOverlayInit reads the boxes up to and including moov: the init segment.
func readOverlayInit(reader io.Reader) ([]byte, error) {
	return readBoxesThrough(reader, "moov")
}

// readOverlayFragment reads the boxes up to and including the next mdat: one moof+mdat fragment.
// It returns io.EOF when the stream ended between fragments.
func readOverlayFragment(reader io.Reader) ([]byte, error) {
	return readBoxesThrough(reader, "mdat")
}

func readBoxesThrough(reader io.Reader, lastType string) ([]byte, error) {
	var collected []byte
	for {
		boxType, box, err := readBox(reader)
		if errors.Is(err, io.EOF) && len(collected) > 0 {
			return nil, io.ErrUnexpectedEOF
		}
		if err != nil {
			return nil, err
		}
		collected = append(collected, box...)
		if boxType == lastType {
			return collected, nil
		}
	}
}

// readBox reads one top-level MP4 box, header included; io.EOF when none starts.
func readBox(reader io.Reader) (string, []byte, error) {
	header := make([]byte, 8, 16)
	if _, err := io.ReadFull(reader, header); err != nil {
		return "", nil, err
	}
	size := uint64(binary.BigEndian.Uint32(header[:4]))
	boxType := string(header[4:8])
	if size == 1 {
		largeSize := make([]byte, 8)
		if _, err := io.ReadFull(reader, largeSize); err != nil {
			return "", nil, unexpectedEOF(err)
		}
		size = binary.BigEndian.Uint64(largeSize)
		header = append(header, largeSize...)
	}
	if size < uint64(len(header)) || size > maxOverlayBoxBytes {
		return "", nil, fmt.Errorf("MP4 box %q has a bad size %d", boxType, size)
	}
	box := make([]byte, size)
	copy(box, header)
	if _, err := io.ReadFull(reader, box[len(header):]); err != nil {
		return "", nil, unexpectedEOF(err)
	}
	return boxType, box, nil
}

// unexpectedEOF reports a stream that ended inside a box.
func unexpectedEOF(err error) error {
	if errors.Is(err, io.EOF) {
		return io.ErrUnexpectedEOF
	}
	return err
}
