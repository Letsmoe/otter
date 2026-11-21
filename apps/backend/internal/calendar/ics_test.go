package calendar

import (
	"strings"
	"testing"
	"time"
	"unicode/utf8"
)

var testNow = time.Date(2026, 3, 1, 12, 30, 0, 0, time.UTC)

func TestRenderWritesAnAllDayEventPerEpisode(t *testing.T) {
	events := []Event{{
		UID: "episode-62085@otter", Summary: "Bosch: S7E3 Long Way Home", Description: "Harry returns.",
		Day: time.Date(2026, 3, 12, 0, 0, 0, 0, time.UTC),
	}}

	document := Render("Otter: Alex", events, testNow, 6*time.Hour)

	for _, want := range []string{
		"BEGIN:VCALENDAR\r\n", "VERSION:2.0\r\n", "X-WR-CALNAME:Otter: Alex\r\n",
		"REFRESH-INTERVAL;VALUE=DURATION:PT6H\r\n",
		"BEGIN:VEVENT\r\n", "UID:episode-62085@otter\r\n", "DTSTAMP:20260301T123000Z\r\n",
		"DTSTART;VALUE=DATE:20260312\r\n", "DTEND;VALUE=DATE:20260313\r\n",
		"SUMMARY:Bosch: S7E3 Long Way Home\r\n", "DESCRIPTION:Harry returns.\r\n",
		"END:VEVENT\r\n", "END:VCALENDAR\r\n",
	} {
		if !strings.Contains(document, want) {
			t.Errorf("missing %q in\n%s", want, document)
		}
	}
}

func TestAnEventOnTheLastDayOfAMonthEndsOnTheFirstOfTheNext(t *testing.T) {
	events := []Event{{UID: "a", Summary: "a", Day: time.Date(2026, 3, 31, 0, 0, 0, 0, time.UTC)}}

	document := Render("x", events, testNow, 24*time.Hour)

	if !strings.Contains(document, "DTEND;VALUE=DATE:20260401\r\n") {
		t.Fatalf("got\n%s", document)
	}
	if !strings.Contains(document, "REFRESH-INTERVAL;VALUE=DURATION:P1D\r\n") {
		t.Fatalf("a day is not spelled P1D:\n%s", document)
	}
}

func TestTextIsEscaped(t *testing.T) {
	got := escapeText("a, b; c\\d\ne")

	if got != "a\\, b\\; c\\\\d\\ne" {
		t.Fatalf("got %q", got)
	}
}

func TestLongLinesAreFoldedWithoutCuttingCharacters(t *testing.T) {
	var document strings.Builder

	writeLine(&document, "SUMMARY:"+strings.Repeat("Ä", 100))

	lines := strings.Split(strings.TrimSuffix(document.String(), "\r\n"), "\r\n")
	if len(lines) < 3 {
		t.Fatalf("not folded: %q", lines)
	}
	for index, line := range lines {
		if len(line) > maxLineBytes {
			t.Errorf("line %d is %d bytes", index, len(line))
		}
		if index > 0 && !strings.HasPrefix(line, " ") {
			t.Errorf("continuation %d does not start with a space", index)
		}
		if !utf8.ValidString(line) {
			t.Errorf("line %d cuts a character", index)
		}
	}
	unfolded := strings.ReplaceAll(strings.TrimSuffix(document.String(), "\r\n"), "\r\n ", "")
	if unfolded != "SUMMARY:"+strings.Repeat("Ä", 100) {
		t.Errorf("unfolding does not give the line back")
	}
}
