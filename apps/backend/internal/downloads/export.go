package downloads

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/storage"
)

// ErrNotExportable marks HLS downloads whose segments only play with
// their playlist (encrypted, or fMP4 with a remote init segment).
var ErrNotExportable = errors.New("download cannot be saved as a single file")

// Exporter mirrors finished downloads into a folder as readably named files:
// "Movie (2020).mp4" and "Series/Season 01/Series S01E02 - Name.ts".
type Exporter struct {
	Dir     string
	storage *storage.Client
	library *library.Store
}

func NewExporter(dir string, store *storage.Client, libraryStore *library.Store) *Exporter {
	return &Exporter{Dir: dir, storage: store, library: libraryStore}
}

// Export writes the download's file unless it already exists.
func (e *Exporter) Export(ctx context.Context, download Download) error {
	target, err := e.filePath(ctx, download)
	if err != nil {
		return err
	}
	if _, err := os.Stat(target); err == nil {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	key, ok := storage.KeyFromURL(download.FileURL)
	if !ok {
		return fmt.Errorf("not a blob URL: %q", download.FileURL)
	}
	if isHLSMime(download.MimeType) {
		return e.writeSegments(ctx, key, target)
	}
	return e.linkOrCopy(key, target)
}

// Remove deletes the download's file and the folders it leaves empty.
func (e *Exporter) Remove(ctx context.Context, download Download) error {
	target, err := e.filePath(ctx, download)
	if err != nil {
		return err
	}
	if err := os.Remove(target); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	e.removeEmptyParents(filepath.Dir(target))
	return nil
}

func (e *Exporter) removeEmptyParents(directory string) {
	root := filepath.Clean(e.Dir)
	for directory != root && strings.HasPrefix(directory, root) {
		// os.Remove refuses non-empty directories.
		if os.Remove(directory) != nil {
			return
		}
		directory = filepath.Dir(directory)
	}
}

func (e *Exporter) filePath(ctx context.Context, download Download) (string, error) {
	extension := ".mp4"
	if isHLSMime(download.MimeType) {
		extension = ".ts"
	}
	if download.MediaType == library.MediaTypeEpisode {
		return e.episodePath(ctx, download, extension)
	}
	return e.moviePath(ctx, download, extension)
}

func (e *Exporter) moviePath(ctx context.Context, download Download, extension string) (string, error) {
	movie, err := e.library.Title(ctx, download.Ref())
	if err != nil {
		return "", err
	}
	if movie == nil {
		return "", fmt.Errorf("movie %d not found", download.MediaID)
	}
	name := movie.Title
	if movie.Year() > 0 {
		name = fmt.Sprintf("%s (%d)", movie.Title, movie.Year())
	}
	return filepath.Join(e.Dir, safeFileName(name)+extension), nil
}

func (e *Exporter) episodePath(ctx context.Context, download Download, extension string) (string, error) {
	episode, err := e.library.Episode(ctx, download.MediaID)
	if err != nil {
		return "", err
	}
	if episode == nil {
		return "", fmt.Errorf("episode %d not found", download.MediaID)
	}
	series, err := e.library.Title(ctx, library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: episode.SeriesID})
	if err != nil {
		return "", err
	}
	if series == nil {
		return "", fmt.Errorf("series %d not found", episode.SeriesID)
	}
	name := fmt.Sprintf("%s S%02dE%02d", series.Title, episode.SeasonNumber, episode.EpisodeNumber)
	if episode.Name != "" {
		name += " - " + episode.Name
	}
	seasonFolder := fmt.Sprintf("Season %02d", episode.SeasonNumber)
	return filepath.Join(e.Dir, safeFileName(series.Title), seasonFolder, safeFileName(name)+extension), nil
}

// linkOrCopy hardlinks the blob so the export takes no extra space, and
// copies it when the folder is on another file system.
func (e *Exporter) linkOrCopy(key, target string) error {
	source, err := e.storage.Path(key)
	if err != nil {
		return err
	}
	if os.Link(source, target) == nil {
		return nil
	}
	return writeAtomically(target, func(file *os.File) error {
		return appendFile(file, source)
	})
}

// writeSegments joins the segments of a stored HLS playlist into one file;
// MPEG-TS segments play back to back.
func (e *Exporter) writeSegments(ctx context.Context, manifestKey, target string) error {
	segmentKeys, err := e.segmentKeys(ctx, manifestKey)
	if err != nil {
		return err
	}
	return writeAtomically(target, func(file *os.File) error {
		return e.appendSegments(file, segmentKeys)
	})
}

func (e *Exporter) appendSegments(file *os.File, segmentKeys []string) error {
	for _, key := range segmentKeys {
		source, err := e.storage.Path(key)
		if err != nil {
			return err
		}
		if err := appendFile(file, source); err != nil {
			return err
		}
	}
	return nil
}

func (e *Exporter) segmentKeys(ctx context.Context, manifestKey string) ([]string, error) {
	reader, err := e.storage.Get(ctx, manifestKey)
	if err != nil {
		return nil, err
	}
	defer reader.Close()
	var keys []string
	scanner := bufio.NewScanner(reader)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if needsPlaylist(line) {
			return nil, ErrNotExportable
		}
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, ok := storage.KeyFromURL(line)
		if !ok {
			return nil, fmt.Errorf("segment not stored: %q", line)
		}
		keys = append(keys, key)
	}
	return keys, scanner.Err()
}

func needsPlaylist(line string) bool {
	if strings.HasPrefix(line, "#EXT-X-MAP") {
		return true
	}
	return strings.HasPrefix(line, "#EXT-X-KEY") && !strings.Contains(line, "METHOD=NONE")
}

// writeAtomically lets write fill a temporary file that only replaces target
// once complete, so the folder never shows half-written videos.
func writeAtomically(target string, write func(file *os.File) error) error {
	temporary, err := os.CreateTemp(filepath.Dir(target), ".export-*")
	if err != nil {
		return err
	}
	defer os.Remove(temporary.Name())
	if err := write(temporary); err != nil {
		temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	return os.Rename(temporary.Name(), target)
}

func appendFile(destination io.Writer, sourcePath string) error {
	source, err := os.Open(sourcePath)
	if err != nil {
		return err
	}
	defer source.Close()
	_, err = io.Copy(destination, source)
	return err
}

func isHLSMime(mimeType string) bool {
	return strings.Contains(strings.ToLower(mimeType), "mpegurl")
}

// safeFileName drops characters file systems or file managers reject.
func safeFileName(name string) string {
	cleaned := strings.Map(func(character rune) rune {
		if strings.ContainsRune(`/\:*?"<>|`, character) || character < 32 {
			return -1
		}
		return character
	}, name)
	cleaned = strings.Trim(strings.TrimSpace(cleaned), ".")
	if cleaned == "" {
		return "Untitled"
	}
	return cleaned
}
