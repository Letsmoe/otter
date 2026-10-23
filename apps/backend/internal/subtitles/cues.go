// Package subtitles finds caption files through the subtitles plugins, cleans
// them up and serves them as WebVTT and SRT.
package subtitles

import (
	"html"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// minimumCueMillis is the length given to a cue whose end does not come after
// its start.
const minimumCueMillis = 1500

// advertisementMarkers are lowercase snippets that mark a cue as an ad some
// subtitle sources add to their files.
var advertisementMarkers = []string{
	"getray.app",
	"tryray.app",
	"opensubtitles",
	"untertiteln zu suchen",
	"advertise your product",
}

// Cue is one caption: when it shows and what it says. Text keeps <i>, <b> and
// <u> tags and holds every other character as is (no HTML entities).
type Cue struct {
	StartMillis int
	EndMillis   int
	Text        string
}

const timestampPattern = `(?:(\d+):)?(\d+):(\d+)[,.:](\d+)`

var (
	timingLine     = regexp.MustCompile(`^\s*` + timestampPattern + `\s*-+>\s*` + timestampPattern)
	indexLine      = regexp.MustCompile(`^\s*\d+\s*$`)
	assOverride    = regexp.MustCompile(`\{\\[^}]*\}`)
	markupTag      = regexp.MustCompile(`(?i)</?(?:i|b|u)>`)
	anyTag         = regexp.MustCompile(`</?[a-zA-Z][^>]*>`)
	lineBreakTag   = regexp.MustCompile(`(?i)<br\s*/?>`)
	allowedTagName = regexp.MustCompile(`(?i)^</?(?:i|b|u)>$`)
)

// ParseCues reads SRT or WebVTT text leniently: timestamps may lack hours or
// digits, use "." for ",", and cues need no blank line between them. Cues are
// returned sorted, with ads and empty cues removed.
func ParseCues(content string) []Cue {
	lines := splitLines(content)
	var cues []Cue
	for position := 0; position < len(lines); position++ {
		cue, last, found := readCue(lines, position)
		if !found {
			continue
		}
		position = last
		cues = appendCleaned(cues, cue)
	}
	sort.SliceStable(cues, func(left, right int) bool { return cues[left].StartMillis < cues[right].StartMillis })
	return cues
}

func splitLines(content string) []string {
	content = strings.ReplaceAll(content, "\r\n", "\n")
	content = strings.ReplaceAll(content, "\r", "\n")
	return strings.Split(content, "\n")
}

// readCue reads the cue whose timing line is at start; last is the index of
// its final line.
func readCue(lines []string, start int) (cue Cue, last int, found bool) {
	startMillis, endMillis, matched := parseTiming(lines[start])
	if !matched {
		return Cue{}, start, false
	}
	var textLines []string
	last = start
	for next := start + 1; next < len(lines); next++ {
		if endsCue(lines, next) {
			break
		}
		textLines = append(textLines, lines[next])
		last = next
	}
	cue = Cue{StartMillis: startMillis, EndMillis: endMillis, Text: strings.Join(textLines, "\n")}
	return cue, last, true
}

// endsCue reports whether the line at index is a blank line or the index
// number that precedes the next timing line.
func endsCue(lines []string, index int) bool {
	if strings.TrimSpace(lines[index]) == "" {
		return true
	}
	if _, _, isTiming := parseTiming(lines[index]); isTiming {
		return true
	}
	if index+1 >= len(lines) || !indexLine.MatchString(lines[index]) {
		return false
	}
	_, _, nextIsTiming := parseTiming(lines[index+1])
	return nextIsTiming
}

func parseTiming(line string) (startMillis, endMillis int, matched bool) {
	groups := timingLine.FindStringSubmatch(line)
	if groups == nil {
		return 0, 0, false
	}
	return millisOf(groups[1:5]), millisOf(groups[5:9]), true
}

// millisOf converts [hours, minutes, seconds, fraction] captures; the
// fraction is read as decimal digits ("5" is 500 ms).
func millisOf(parts []string) int {
	hours, _ := strconv.Atoi(parts[0])
	minutes, _ := strconv.Atoi(parts[1])
	seconds, _ := strconv.Atoi(parts[2])
	fraction := (parts[3] + "000")[:3]
	millis, _ := strconv.Atoi(fraction)
	return ((hours*60+minutes)*60+seconds)*1000 + millis
}

func appendCleaned(cues []Cue, cue Cue) []Cue {
	cue.Text = cleanText(cue.Text)
	if hasNoWords(cue.Text) || isAdvertisement(cue.Text) {
		return cues
	}
	if cue.EndMillis <= cue.StartMillis {
		cue.EndMillis = cue.StartMillis + minimumCueMillis
	}
	return append(cues, cue)
}

// cleanText keeps <i>, <b>, <u>, drops every other tag and ASS override
// block, and decodes HTML entities in the text between them.
func cleanText(text string) string {
	text = assOverride.ReplaceAllString(text, "")
	text = lineBreakTag.ReplaceAllString(text, "\n")
	text = anyTag.ReplaceAllStringFunc(text, keepAllowedTag)
	var cleaned strings.Builder
	for _, segment := range splitMarkup(text) {
		if segment.isTag {
			cleaned.WriteString(strings.ToLower(segment.text))
			continue
		}
		cleaned.WriteString(html.UnescapeString(segment.text))
	}
	return trimLines(cleaned.String())
}

func hasNoWords(text string) bool {
	return strings.TrimSpace(markupTag.ReplaceAllString(text, "")) == ""
}

func keepAllowedTag(tag string) string {
	if allowedTagName.MatchString(tag) {
		return tag
	}
	return ""
}

func trimLines(text string) string {
	var kept []string
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if line != "" {
			kept = append(kept, line)
		}
	}
	return strings.Join(kept, "\n")
}

func isAdvertisement(text string) bool {
	lowered := strings.ToLower(text)
	for _, marker := range advertisementMarkers {
		if strings.Contains(lowered, marker) {
			return true
		}
	}
	return false
}

// textSegment is a run of cue text, either a markup tag or plain characters.
type textSegment struct {
	text  string
	isTag bool
}

func splitMarkup(text string) []textSegment {
	var segments []textSegment
	previous := 0
	for _, bounds := range markupTag.FindAllStringIndex(text, -1) {
		if bounds[0] > previous {
			segments = append(segments, textSegment{text: text[previous:bounds[0]]})
		}
		segments = append(segments, textSegment{text: text[bounds[0]:bounds[1]], isTag: true})
		previous = bounds[1]
	}
	if previous < len(text) {
		segments = append(segments, textSegment{text: text[previous:]})
	}
	return segments
}

// ShiftCues moves every cue by offsetMillis (negative = earlier). Cues that
// end before the start of the video are dropped.
func ShiftCues(cues []Cue, offsetMillis int) []Cue {
	if offsetMillis == 0 {
		return cues
	}
	shifted := make([]Cue, 0, len(cues))
	for _, cue := range cues {
		cue.StartMillis += offsetMillis
		cue.EndMillis += offsetMillis
		if cue.EndMillis <= 0 {
			continue
		}
		if cue.StartMillis < 0 {
			cue.StartMillis = 0
		}
		shifted = append(shifted, cue)
	}
	return shifted
}

// RenderSRT writes cues as a numbered SRT file.
func RenderSRT(cues []Cue) string {
	var output strings.Builder
	for index, cue := range cues {
		output.WriteString(strconv.Itoa(index + 1))
		output.WriteString("\n")
		output.WriteString(formatTimestamp(cue.StartMillis, ","))
		output.WriteString(" --> ")
		output.WriteString(formatTimestamp(cue.EndMillis, ","))
		output.WriteString("\n")
		output.WriteString(cue.Text)
		output.WriteString("\n\n")
	}
	return output.String()
}

// RenderVTT writes cues as a WebVTT file, escaping the characters VTT reads
// as markup.
func RenderVTT(cues []Cue) string {
	var output strings.Builder
	output.WriteString("WEBVTT\n\n")
	for _, cue := range cues {
		output.WriteString(formatTimestamp(cue.StartMillis, "."))
		output.WriteString(" --> ")
		output.WriteString(formatTimestamp(cue.EndMillis, "."))
		output.WriteString("\n")
		output.WriteString(escapeVTT(cue.Text))
		output.WriteString("\n\n")
	}
	return output.String()
}

func escapeVTT(text string) string {
	var escaped strings.Builder
	for _, segment := range splitMarkup(text) {
		if segment.isTag {
			escaped.WriteString(segment.text)
			continue
		}
		plain := strings.ReplaceAll(segment.text, "&", "&amp;")
		plain = strings.ReplaceAll(plain, "<", "&lt;")
		escaped.WriteString(strings.ReplaceAll(plain, ">", "&gt;"))
	}
	return escaped.String()
}

func formatTimestamp(totalMillis int, fractionSeparator string) string {
	hours := totalMillis / 3600000
	minutes := totalMillis / 60000 % 60
	seconds := totalMillis / 1000 % 60
	millis := totalMillis % 1000
	return pad(hours, 2) + ":" + pad(minutes, 2) + ":" + pad(seconds, 2) + fractionSeparator + pad(millis, 3)
}

func pad(number, width int) string {
	text := strconv.Itoa(number)
	for len(text) < width {
		text = "0" + text
	}
	return text
}
