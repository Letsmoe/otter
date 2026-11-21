// Package calendar serves a profile's upcoming episodes as an iCalendar feed
// that calendar apps subscribe to.
package calendar

import (
	"strconv"
	"strings"
	"time"
)

// Event is something on one day, shown as an all-day entry.
type Event struct {
	// UID stays the same for the same episode, so a calendar updates it instead of duplicating it.
	UID         string
	Summary     string
	Description string
	Day         time.Time
}

const (
	lineBreak = "\r\n"
	// A content line is folded after this many bytes (RFC 5545 section 3.1).
	maxLineBytes = 75
	dateFormat   = "20060102"
	stampFormat  = "20060102T150405Z"
)

// Render writes the events as an iCalendar document. now stamps the events;
// refreshHint tells calendar apps how often to ask again.
func Render(name string, events []Event, now time.Time, refreshHint time.Duration) string {
	var document strings.Builder
	writeLine(&document, "BEGIN:VCALENDAR")
	writeLine(&document, "VERSION:2.0")
	writeLine(&document, "PRODID:-//Otter//Upcoming episodes//EN")
	writeLine(&document, "CALSCALE:GREGORIAN")
	writeLine(&document, "METHOD:PUBLISH")
	writeLine(&document, "X-WR-CALNAME:"+escapeText(name))
	writeLine(&document, "REFRESH-INTERVAL;VALUE=DURATION:"+durationText(refreshHint))
	writeLine(&document, "X-PUBLISHED-TTL:"+durationText(refreshHint))
	for _, event := range events {
		writeEvent(&document, event, now)
	}
	writeLine(&document, "END:VCALENDAR")
	return document.String()
}

func writeEvent(document *strings.Builder, event Event, now time.Time) {
	writeLine(document, "BEGIN:VEVENT")
	writeLine(document, "UID:"+event.UID)
	writeLine(document, "DTSTAMP:"+now.UTC().Format(stampFormat))
	writeLine(document, "DTSTART;VALUE=DATE:"+event.Day.Format(dateFormat))
	writeLine(document, "DTEND;VALUE=DATE:"+event.Day.AddDate(0, 0, 1).Format(dateFormat))
	writeLine(document, "SUMMARY:"+escapeText(event.Summary))
	if event.Description != "" {
		writeLine(document, "DESCRIPTION:"+escapeText(event.Description))
	}
	writeLine(document, "TRANSP:TRANSPARENT")
	writeLine(document, "END:VEVENT")
}

// durationText is a duration as iCalendar spells it, e.g. "PT6H" or "P1D".
func durationText(duration time.Duration) string {
	hours := max(int(duration.Hours()), 1)
	if hours%24 == 0 {
		return "P" + strconv.Itoa(hours/24) + "D"
	}
	return "PT" + strconv.Itoa(hours) + "H"
}

var textEscaper = strings.NewReplacer("\\", "\\\\", ";", "\\;", ",", "\\,", "\r\n", "\\n", "\n", "\\n", "\r", "\\n")

// escapeText escapes what iCalendar text values may not contain raw; line breaks become \n.
func escapeText(text string) string {
	return textEscaper.Replace(text)
}

// writeLine writes a content line, folded at maxLineBytes without cutting a UTF-8 character.
// Continuation lines start with a space, which counts toward their length.
func writeLine(document *strings.Builder, line string) {
	limit := maxLineBytes
	for len(line) > limit {
		cut := limit
		for cut > 0 && !startsCharacter(line[cut]) {
			cut--
		}
		document.WriteString(line[:cut] + lineBreak + " ")
		line = line[cut:]
		limit = maxLineBytes - 1
	}
	document.WriteString(line + lineBreak)
}

// startsCharacter is false for the continuation bytes of a multi-byte UTF-8 character.
func startsCharacter(value byte) bool {
	return value&0xC0 != 0x80
}
