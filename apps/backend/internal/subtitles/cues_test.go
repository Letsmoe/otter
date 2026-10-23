package subtitles

import (
	"strings"
	"testing"
)

func TestParseCuesReadsStandardSRT(t *testing.T) {
	cues := ParseCues("1\n00:00:01,000 --> 00:00:03,500\nHello there\n\n2\n00:01:00,250 --> 00:01:02,000\nSecond\nline\n")
	if len(cues) != 2 {
		t.Fatalf("got %d cues: %+v", len(cues), cues)
	}
	if cues[0].StartMillis != 1000 || cues[0].EndMillis != 3500 || cues[0].Text != "Hello there" {
		t.Errorf("first cue: %+v", cues[0])
	}
	if cues[1].StartMillis != 60250 || cues[1].Text != "Second\nline" {
		t.Errorf("second cue: %+v", cues[1])
	}
}

func TestParseCuesToleratesMalformedTimestamps(t *testing.T) {
	content := "1\n00:0:41,330 --> 00:0:43,5\nshort fields\n\n" +
		"2\n01:02.500 --> 01:04.000\nno hours, dot separator\n\n" +
		"3\n0:00:50,1 --> 0:00:52,25\nfraction digits\n"
	cues := ParseCues(content)
	if len(cues) != 3 {
		t.Fatalf("got %d cues: %+v", len(cues), cues)
	}
	if cues[0].StartMillis != 41330 || cues[0].EndMillis != 43500 {
		t.Errorf("short fields: %+v", cues[0])
	}
	if cues[2].StartMillis != 62500 || cues[2].EndMillis != 64000 {
		t.Errorf("missing hours: %+v", cues[2])
	}
	if cues[1].StartMillis != 50100 || cues[1].EndMillis != 52250 {
		t.Errorf("fraction digits: %+v", cues[1])
	}
}

func TestParseCuesWithoutBlankLinesBetweenCues(t *testing.T) {
	content := "1\r\n00:00:01,000 --> 00:00:02,000\r\nfirst\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nsecond\r\n"
	cues := ParseCues(content)
	if len(cues) != 2 || cues[0].Text != "first" || cues[1].Text != "second" {
		t.Fatalf("unexpected cues: %+v", cues)
	}
}

func TestParseCuesKeepsNumbersInsideText(t *testing.T) {
	cues := ParseCues("1\n00:00:01,000 --> 00:00:02,000\nIt happened in\n1999\n")
	if len(cues) != 1 || cues[0].Text != "It happened in\n1999" {
		t.Fatalf("unexpected cues: %+v", cues)
	}
}

func TestParseCuesReadsWebVTT(t *testing.T) {
	content := "WEBVTT\n\nNOTE a comment\n\nintro\n00:01.000 --> 00:02.000 align:start\nHi\n"
	cues := ParseCues(content)
	if len(cues) != 1 || cues[0].StartMillis != 1000 || cues[0].Text != "Hi" {
		t.Fatalf("unexpected cues: %+v", cues)
	}
}

func TestParseCuesDropsAdvertisements(t *testing.T) {
	content := "1\n00:00:06,000 --> 00:00:12,000\nPlay drücken.\nÜber 200 Sprachen. Eine App:  tryray.app\n\n" +
		"2\n00:01:00,000 --> 00:01:02,000\nReal line\n\n" +
		"3\n01:30:00,000 --> 01:30:05,000\nSupport us - Advertise your product here\n\n" +
		"4\n01:31:00,000 --> 01:31:05,000\nSubtitles by OpenSubtitles.org\n"
	cues := ParseCues(content)
	if len(cues) != 1 || cues[0].Text != "Real line" {
		t.Fatalf("unexpected cues: %+v", cues)
	}
}

func TestParseCuesCleansMarkup(t *testing.T) {
	content := "1\n00:00:01,000 --> 00:00:02,000\n{\\an8}<font color=\"#fff\"><I>Tom &amp; Jerry</I></font><br/>&lt;fin&gt;\n\n" +
		"2\n00:00:03,000 --> 00:00:04,000\n   \n\n" +
		"3\n00:00:05,000 --> 00:00:06,000\n<b></b>\n"
	cues := ParseCues(content)
	if len(cues) != 1 {
		t.Fatalf("empty cues should go: %+v", cues)
	}
	if cues[0].Text != "<i>Tom & Jerry</i>\n<fin>" {
		t.Errorf("text = %q", cues[0].Text)
	}
}

func TestParseCuesSortsAndFixesBackwardsTimings(t *testing.T) {
	content := "1\n00:00:09,000 --> 00:00:10,000\nlate\n\n2\n00:00:01,000 --> 00:00:01,000\nzero length\n"
	cues := ParseCues(content)
	if len(cues) != 2 || cues[0].Text != "zero length" {
		t.Fatalf("cues should be sorted: %+v", cues)
	}
	if cues[0].EndMillis <= cues[0].StartMillis {
		t.Errorf("end must come after start: %+v", cues[0])
	}
}

func TestRenderVTTEscapesAndConverts(t *testing.T) {
	cues := []Cue{{StartMillis: 3723004, EndMillis: 3725000, Text: "<i>a & b</i>\n1 < 2"}}
	got := RenderVTT(cues)
	want := "WEBVTT\n\n01:02:03.004 --> 01:02:05.000\n<i>a &amp; b</i>\n1 &lt; 2\n\n"
	if got != want {
		t.Errorf("vtt = %q, want %q", got, want)
	}
}

func TestRenderSRTRoundTrips(t *testing.T) {
	original := []Cue{
		{StartMillis: 1000, EndMillis: 2000, Text: "one"},
		{StartMillis: 5500, EndMillis: 7250, Text: "<i>two</i>\nlines"},
	}
	rendered := RenderSRT(original)
	if !strings.HasPrefix(rendered, "1\n00:00:01,000 --> 00:00:02,000\none\n\n2\n") {
		t.Errorf("unexpected srt: %q", rendered)
	}
	parsed := ParseCues(rendered)
	if len(parsed) != 2 || parsed[1] != original[1] {
		t.Errorf("round trip changed the cues: %+v", parsed)
	}
}

func TestShiftCues(t *testing.T) {
	cues := []Cue{
		{StartMillis: 500, EndMillis: 900, Text: "gone"},
		{StartMillis: 1000, EndMillis: 3000, Text: "clamped"},
		{StartMillis: 5000, EndMillis: 6000, Text: "moved"},
	}
	earlier := ShiftCues(cues, -2000)
	if len(earlier) != 2 || earlier[0].StartMillis != 0 || earlier[0].EndMillis != 1000 || earlier[1].StartMillis != 3000 {
		t.Errorf("shifted earlier: %+v", earlier)
	}
	later := ShiftCues(cues, 1500)
	if later[0].StartMillis != 2000 || later[2].EndMillis != 7500 {
		t.Errorf("shifted later: %+v", later)
	}
	if same := ShiftCues(cues, 0); len(same) != 3 {
		t.Errorf("zero offset must keep every cue")
	}
}
