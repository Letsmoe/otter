package subtitles

import (
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// maxPerLanguage is how many files of one language are offered.
const maxPerLanguage = 3

// hearingImpairedSuffix marks files that also describe sounds.
const hearingImpairedSuffix = " (SDH)"

// PreferredLanguages lists the languages to offer captions in, the caption
// language first, then the profile's audio languages, without duplicates.
func PreferredLanguages(captionLanguage string, audioLanguages []string) []string {
	candidates := append([]string{captionLanguage}, audioLanguages...)
	seen := map[string]bool{}
	preferred := []string{}
	for _, candidate := range candidates {
		code := strings.ToLower(strings.TrimSpace(candidate))
		if code == "" || seen[code] {
			continue
		}
		seen[code] = true
		preferred = append(preferred, code)
	}
	return preferred
}

// Rank orders files best first: by the preferred languages in order, regular
// files before hearing-impaired ones, at most maxPerLanguage per language,
// without the same release twice. Languages outside preferred are dropped.
func Rank(files []Subtitle, preferred []string) []Subtitle {
	ranked := []Subtitle{}
	for _, language := range preferred {
		ranked = append(ranked, bestOfLanguage(files, language)...)
	}
	return labelled(ranked)
}

func bestOfLanguage(files []Subtitle, language string) []Subtitle {
	var ofLanguage []Subtitle
	for _, file := range files {
		if file.Language == language {
			ofLanguage = append(ofLanguage, file)
		}
	}
	sort.SliceStable(ofLanguage, func(left, right int) bool { return better(ofLanguage[left], ofLanguage[right]) })
	return firstDistinct(ofLanguage, maxPerLanguage)
}

func better(left, right Subtitle) bool {
	if left.HearingImpaired != right.HearingImpaired {
		return !left.HearingImpaired
	}
	if left.Position != right.Position {
		return left.Position < right.Position
	}
	if left.Plugin != right.Plugin {
		return left.Plugin < right.Plugin
	}
	return left.ID < right.ID
}

// firstDistinct keeps up to limit files, skipping a file whose release and
// hearing-impaired flag an earlier one already has (two plugins often return
// the same file).
func firstDistinct(files []Subtitle, limit int) []Subtitle {
	seen := map[string]bool{}
	kept := []Subtitle{}
	for _, file := range files {
		key := releaseKey(file)
		if seen[key] {
			continue
		}
		seen[key] = true
		kept = append(kept, file)
		if len(kept) == limit {
			break
		}
	}
	return kept
}

var releaseNoise = regexp.MustCompile(`(?i)\.(srt|vtt)$|[^a-z0-9]+`)

func releaseKey(file Subtitle) string {
	release := releaseNoise.ReplaceAllString(file.Release, "")
	if release == "" {
		return file.ID
	}
	return strings.ToLower(release) + "|" + strconv.FormatBool(file.HearingImpaired)
}

// labelled sets each file's display label: the language's own name, " (SDH)"
// for hearing-impaired files, and a counter when two files would read alike.
func labelled(files []Subtitle) []Subtitle {
	counts := map[string]int{}
	for index := range files {
		label := languageName(files[index].Language)
		if files[index].HearingImpaired {
			label += hearingImpairedSuffix
		}
		counts[label]++
		if counts[label] > 1 {
			label += " " + strconv.Itoa(counts[label])
		}
		files[index].Label = label
	}
	return files
}
