package pluginstore

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// install writes the bundle into folder: plugin.js first, package.json last,
// because the manifest is what makes the plugin registry reload the plugin.
func install(folder string, files map[string][]byte) error {
	for _, bundleFile := range bundleFiles {
		if err := writeAtomically(filepath.Join(folder, bundleFile), files[bundleFile]); err != nil {
			return err
		}
	}
	return nil
}

// writeAtomically writes via a hidden temp file and a rename, so readers never see half a file.
func writeAtomically(path string, content []byte) error {
	temporary := filepath.Join(filepath.Dir(path), "."+filepath.Base(path)+".tmp")
	if err := os.WriteFile(temporary, content, 0o644); err != nil {
		return err
	}
	if err := os.Rename(temporary, path); err != nil {
		os.Remove(temporary)
		return err
	}
	return nil
}

// isNewer reports whether candidate is a higher semver than current.
// A pre-release ("1.2.0-beta.1") ranks below its release ("1.2.0").
func isNewer(candidate, current string) bool {
	return compareVersions(candidate, current) > 0
}

func compareVersions(left, right string) int {
	leftCore, leftPrerelease, _ := strings.Cut(strings.TrimPrefix(left, "v"), "-")
	rightCore, rightPrerelease, _ := strings.Cut(strings.TrimPrefix(right, "v"), "-")
	if order := compareCores(leftCore, rightCore); order != 0 {
		return order
	}
	return comparePrereleases(leftPrerelease, rightPrerelease)
}

func compareCores(left, right string) int {
	leftParts := strings.Split(left, ".")
	rightParts := strings.Split(right, ".")
	for index := 0; index < 3; index++ {
		leftNumber := versionPart(leftParts, index)
		rightNumber := versionPart(rightParts, index)
		if leftNumber != rightNumber {
			return sign(leftNumber - rightNumber)
		}
	}
	return 0
}

// comparePrereleases ranks a release (no pre-release tag) above any pre-release.
func comparePrereleases(left, right string) int {
	if left == right {
		return 0
	}
	if left == "" {
		return 1
	}
	if right == "" {
		return -1
	}
	return strings.Compare(left, right)
}

func versionPart(parts []string, index int) int {
	if index >= len(parts) {
		return 0
	}
	number, err := strconv.Atoi(parts[index])
	if err != nil {
		return 0
	}
	return number
}

func sign(value int) int {
	if value > 0 {
		return 1
	}
	if value < 0 {
		return -1
	}
	return 0
}
