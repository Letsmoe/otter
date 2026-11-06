package pluginstore

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha512"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

const (
	maxMetadataBytes = 1 << 20
	maxTarballBytes  = 20 << 20
	maxFileBytes     = 20 << 20
)

// bundleFiles are the files of a release that make up an installed plugin.
var bundleFiles = []string{"plugin.js", "package.json"}

// release is the registry's answer for <package>/latest.
type release struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Dist    struct {
		Tarball   string `json:"tarball"`
		Integrity string `json:"integrity"`
	} `json:"dist"`
}

func (s *Service) latestRelease(ctx context.Context, packageName string) (release, error) {
	var latest release
	// Scoped names escape their slash: @scope%2Fname.
	address := s.registryURL + "/" + url.PathEscape(packageName) + "/latest"
	body, err := s.fetch(ctx, address, maxMetadataBytes)
	if err != nil {
		return latest, err
	}
	if err := json.Unmarshal(body, &latest); err != nil {
		return latest, fmt.Errorf("parse registry answer: %w", err)
	}
	if latest.Version == "" || latest.Dist.Tarball == "" {
		return latest, errors.New("registry answer has no version or tarball")
	}
	return latest, nil
}

// downloadRelease fetches a release's tarball, checks its integrity and returns the bundle files.
func (s *Service) downloadRelease(ctx context.Context, latest release) (map[string][]byte, error) {
	tarball, err := s.fetch(ctx, latest.Dist.Tarball, maxTarballBytes)
	if err != nil {
		return nil, err
	}
	if err := verifyIntegrity(tarball, latest.Dist.Integrity); err != nil {
		return nil, err
	}
	return extractBundle(tarball)
}

func (s *Service) fetch(ctx context.Context, address string, limit int64) ([]byte, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return nil, err
	}
	response, err := s.client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GET %s: %s", address, response.Status)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(len(body)) > limit {
		return nil, fmt.Errorf("GET %s: larger than %d bytes", address, limit)
	}
	return body, nil
}

// verifyIntegrity checks data against an SRI string ("sha512-<base64>").
func verifyIntegrity(data []byte, integrity string) error {
	for _, entry := range strings.Fields(integrity) {
		encoded, isSha512 := strings.CutPrefix(entry, "sha512-")
		if !isSha512 {
			continue
		}
		digest := sha512.Sum512(data)
		if base64.StdEncoding.EncodeToString(digest[:]) == encoded {
			return nil
		}
		return errors.New("tarball does not match its sha512 integrity")
	}
	return errors.New("release has no sha512 integrity")
}

// extractBundle reads package/plugin.js and package/package.json from a .tgz.
func extractBundle(tarball []byte) (map[string][]byte, error) {
	compressed, err := gzip.NewReader(bytes.NewReader(tarball))
	if err != nil {
		return nil, err
	}
	defer compressed.Close()
	archive := tar.NewReader(compressed)
	files := map[string][]byte{}
	for {
		header, err := archive.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
		if err := collectBundleFile(archive, header, files); err != nil {
			return nil, err
		}
	}
	return requireBundleFiles(files)
}

func collectBundleFile(archive *tar.Reader, header *tar.Header, files map[string][]byte) error {
	if header.Typeflag != tar.TypeReg {
		return nil
	}
	name, inPackage := strings.CutPrefix(header.Name, "package/")
	if !inPackage || !isBundleFile(name) {
		return nil
	}
	content, err := io.ReadAll(io.LimitReader(archive, maxFileBytes+1))
	if err != nil {
		return err
	}
	if len(content) > maxFileBytes {
		return fmt.Errorf("%s is larger than %d bytes", name, maxFileBytes)
	}
	files[name] = content
	return nil
}

func isBundleFile(name string) bool {
	for _, bundleFile := range bundleFiles {
		if name == bundleFile {
			return true
		}
	}
	return false
}

func requireBundleFiles(files map[string][]byte) (map[string][]byte, error) {
	for _, bundleFile := range bundleFiles {
		if _, found := files[bundleFile]; !found {
			return nil, fmt.Errorf("tarball has no package/%s", bundleFile)
		}
	}
	return files, nil
}
