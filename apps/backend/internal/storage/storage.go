// Package storage keeps blobs (cached images, stream segments and files,
// plugin icons) as plain files under one directory and serves them over HTTP.
package storage

import (
	"context"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strings"
	"sync/atomic"
	"time"
)

// RoutePrefix is where Handler is mounted; DirectURL builds URLs under it.
const RoutePrefix = "/api/blob/"

func init() {
	// Not every platform's mime table knows the streaming types.
	mime.AddExtensionType(".m3u8", "application/vnd.apple.mpegurl")
	mime.AddExtensionType(".ts", "video/mp2t")
	mime.AddExtensionType(".mp4", "video/mp4")
}

// temporaryPrefix names the files Put writes before renaming them into place.
const temporaryPrefix = ".put-"

// touchInterval throttles last-use bumps: a blob's modification time is only
// moved forward when it is older than this, so range requests don't each cost
// a syscall.
const touchInterval = time.Minute

type Client struct {
	root       string
	publicBase string
	// onPut may be registered while Puts are already running.
	onPut atomic.Pointer[func(key string, size int64)]
}

// File describes a stored blob. Its modification time doubles as the last
// time it was used: Put sets it, Touch and Handler move it forward.
type File struct {
	Key      string
	Size     int64
	LastUsed time.Time
}

// New stores blobs under root and builds URLs from publicBase (the backend's
// externally reachable origin, e.g. "http://localhost:8080").
func New(root, publicBase string) (*Client, error) {
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, fmt.Errorf("create blob dir: %w", err)
	}
	if err := removeTemporary(root); err != nil {
		return nil, fmt.Errorf("remove unfinished blobs: %w", err)
	}
	return &Client{root: root, publicBase: strings.TrimRight(publicBase, "/")}, nil
}

// DirectURL returns the URL Handler serves key at.
func (c *Client) DirectURL(key string) string {
	return c.publicBase + RoutePrefix + key
}

// RelativeURL returns the root-relative path Handler serves key at. Redirects
// use it so they resolve against whatever host the client reached the backend
// on, instead of the configured public origin.
func RelativeURL(key string) string {
	return RoutePrefix + key
}

// Put writes key from r. The file only appears once fully written, so a
// reader never sees a partial blob. size and contentType are accepted for
// interface compatibility; the served type comes from the key's extension.
func (c *Client) Put(ctx context.Context, key string, r io.Reader, size int64, contentType string) error {
	target, err := c.path(key)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(filepath.Dir(target), temporaryPrefix+"*")
	if err != nil {
		return err
	}
	defer os.Remove(temporary.Name())
	written, err := io.Copy(temporary, contextReader{ctx: ctx, reader: r})
	if err != nil {
		temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := os.Rename(temporary.Name(), target); err != nil {
		return err
	}
	if callback := c.onPut.Load(); callback != nil {
		(*callback)(key, written)
	}
	return nil
}

// partialSuffix marks a resumable write in progress. Unlike Put temporaries,
// partial files survive a restart so the write can continue where it stopped.
const partialSuffix = ".partial"

// PartialSize returns how many bytes of key's resumable write are stored; 0
// when there is none.
func (c *Client) PartialSize(key string) int64 {
	target, err := c.path(key)
	if err != nil {
		return 0
	}
	info, err := os.Stat(target + partialSuffix)
	if err != nil {
		return 0
	}
	return info.Size()
}

// PutResumable writes r into key's partial file from offset on (dropping
// anything stored past it) and moves it to key once r is fully read. When r
// fails, the bytes written so far stay for a later call to continue from.
func (c *Client) PutResumable(ctx context.Context, key string, r io.Reader, offset int64) error {
	return c.PutResumableObserved(ctx, key, r, offset, nil)
}

// PutResumableObserved is PutResumable reporting the partial file's size
// after every write, so readers can follow the file while it grows. onWrite
// may be nil.
func (c *Client) PutResumableObserved(ctx context.Context, key string, r io.Reader, offset int64, onWrite func(size int64)) error {
	target, err := c.path(key)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	partial, err := openPartial(target+partialSuffix, offset)
	if err != nil {
		return err
	}
	writer := &observedWriter{writer: partial, size: offset, onWrite: onWrite}
	_, err = io.Copy(writer, contextReader{ctx: ctx, reader: r})
	closeErr := partial.Close()
	if err != nil {
		return err
	}
	if closeErr != nil {
		return closeErr
	}
	return c.finishPartial(key, target)
}

// PartialPath returns the file key's resumable write goes to until it is done.
func (c *Client) PartialPath(key string) (string, error) {
	target, err := c.path(key)
	if err != nil {
		return "", err
	}
	return target + partialSuffix, nil
}

// observedWriter reports the total size after each write.
type observedWriter struct {
	writer  io.Writer
	size    int64
	onWrite func(size int64)
}

func (w *observedWriter) Write(buffer []byte) (int, error) {
	written, err := w.writer.Write(buffer)
	w.size += int64(written)
	if w.onWrite != nil && written > 0 {
		w.onWrite(w.size)
	}
	return written, err
}

// openPartial opens a partial file for writing at offset.
func openPartial(target string, offset int64) (*os.File, error) {
	file, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE, 0o644)
	if err != nil {
		return nil, err
	}
	if err := file.Truncate(offset); err != nil {
		file.Close()
		return nil, err
	}
	if _, err := file.Seek(offset, io.SeekStart); err != nil {
		file.Close()
		return nil, err
	}
	return file, nil
}

func (c *Client) finishPartial(key, target string) error {
	if err := os.Rename(target+partialSuffix, target); err != nil {
		return err
	}
	info, err := os.Stat(target)
	if err != nil {
		return err
	}
	if callback := c.onPut.Load(); callback != nil {
		(*callback)(key, info.Size())
	}
	return nil
}

// Size returns the stored size of key; 0 when it is missing.
func (c *Client) Size(key string) int64 {
	target, err := c.path(key)
	if err != nil {
		return 0
	}
	info, err := os.Stat(target)
	if err != nil {
		return 0
	}
	return info.Size()
}

// OnPut registers a callback run after every successful Put with the key and
// the number of bytes written.
func (c *Client) OnPut(callback func(key string, size int64)) {
	c.onPut.Store(&callback)
}

// Touch marks key as used now. It fails when key is not stored.
func (c *Client) Touch(ctx context.Context, key string) error {
	target, err := c.path(key)
	if err != nil {
		return err
	}
	info, err := os.Stat(target)
	if err != nil {
		return err
	}
	return touchFile(target, info)
}

// touchFile moves a file's modification time to now, unless it was already
// bumped within touchInterval.
func touchFile(target string, info os.FileInfo) error {
	now := time.Now()
	if now.Sub(info.ModTime()) < touchInterval {
		return nil
	}
	return os.Chtimes(target, now, now)
}

// List returns every stored blob under prefix (a key prefix ending in "/").
// Files still being written by Put are left out. A missing prefix is empty.
func (c *Client) List(ctx context.Context, prefix string) ([]File, error) {
	directory, err := c.path(prefix)
	if err != nil {
		return nil, err
	}
	var files []File
	err = filepath.WalkDir(directory, func(target string, entry fs.DirEntry, err error) error {
		if err != nil {
			if os.IsNotExist(err) {
				return nil
			}
			return err
		}
		if entry.IsDir() || strings.HasPrefix(entry.Name(), temporaryPrefix) {
			return nil
		}
		info, err := entry.Info()
		if err != nil {
			// Deleted between listing the directory and reading it.
			return nil
		}
		files = append(files, File{Key: c.key(target), Size: info.Size(), LastUsed: info.ModTime()})
		return nil
	})
	return files, err
}

// removeTemporary deletes every Put temporary file under root. Run before the
// store is used, they can only be leftovers of a previous process killed
// mid-write (a restart during background stream caching leaves whole videos).
func removeTemporary(root string) error {
	return filepath.WalkDir(root, func(target string, entry fs.DirEntry, err error) error {
		if err != nil {
			if os.IsNotExist(err) {
				return nil
			}
			return err
		}
		if entry.IsDir() || !strings.HasPrefix(entry.Name(), temporaryPrefix) {
			return nil
		}
		return os.Remove(target)
	})
}

// KeyFromURL extracts the blob key from a URL built by DirectURL or
// RelativeURL; ok is false for any other URL.
func KeyFromURL(blobURL string) (key string, ok bool) {
	index := strings.Index(blobURL, RoutePrefix)
	if index < 0 {
		return "", false
	}
	key = blobURL[index+len(RoutePrefix):]
	if key == "" {
		return "", false
	}
	return key, true
}

// Get opens key for reading.
func (c *Client) Get(ctx context.Context, key string) (io.ReadCloser, error) {
	target, err := c.path(key)
	if err != nil {
		return nil, err
	}
	return os.Open(target)
}

// Exists reports whether key has been stored.
func (c *Client) Exists(ctx context.Context, key string) bool {
	target, err := c.path(key)
	if err != nil {
		return false
	}
	info, err := os.Stat(target)
	return err == nil && info.Mode().IsRegular()
}

// Delete removes key; a missing key is not an error.
func (c *Client) Delete(ctx context.Context, key string) error {
	target, err := c.path(key)
	if err != nil {
		return err
	}
	if err := os.Remove(target); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

// Handler serves blobs at RoutePrefix + key, with range support for seeking.
func (c *Client) Handler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		target, err := c.path(strings.TrimPrefix(r.URL.Path, RoutePrefix))
		if err != nil {
			http.NotFound(w, r)
			return
		}
		file, err := os.Open(target)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		defer file.Close()
		info, err := file.Stat()
		if err != nil || !info.Mode().IsRegular() {
			http.NotFound(w, r)
			return
		}
		// Serving counts as use, keeping cache blobs in play out of eviction.
		_ = touchFile(target, info)
		w.Header().Set("Cache-Control", "public, max-age=86400")
		http.ServeContent(w, r, info.Name(), info.ModTime(), file)
	})
}

// path maps a key to a file under root. Cleaning against "/" first means
// "../" segments can never climb above root.
// Path returns the file key is stored in.
func (c *Client) Path(key string) (string, error) {
	return c.path(key)
}

func (c *Client) path(key string) (string, error) {
	cleaned := path.Clean("/" + key)
	if cleaned == "/" || strings.Contains(key, "\\") {
		return "", fmt.Errorf("invalid blob key %q", key)
	}
	return filepath.Join(c.root, filepath.FromSlash(cleaned)), nil
}

// key maps a file under root back to its blob key.
func (c *Client) key(target string) string {
	relative, err := filepath.Rel(c.root, target)
	if err != nil {
		return filepath.ToSlash(target)
	}
	return filepath.ToSlash(relative)
}

// contextReader stops a copy once ctx is cancelled.
type contextReader struct {
	ctx    context.Context
	reader io.Reader
}

func (r contextReader) Read(buffer []byte) (int, error) {
	if err := r.ctx.Err(); err != nil {
		return 0, err
	}
	return r.reader.Read(buffer)
}
