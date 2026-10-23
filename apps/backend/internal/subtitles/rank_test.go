package subtitles

import "testing"

func file(id, language, release string, position int, hearingImpaired bool) Subtitle {
	return Subtitle{ID: id, Plugin: "plugin", Language: language, Release: release, Position: position, HearingImpaired: hearingImpaired}
}

func ids(files []Subtitle) []string {
	result := []string{}
	for _, file := range files {
		result = append(result, file.ID)
	}
	return result
}

func equalStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
}

func TestPreferredLanguagesPutsTheCaptionLanguageFirst(t *testing.T) {
	got := PreferredLanguages("en", []string{"de", "en", "fr"})
	if !equalStrings(got, []string{"en", "de", "fr"}) {
		t.Errorf("got %v", got)
	}
	got = PreferredLanguages("", []string{"De", " en "})
	if !equalStrings(got, []string{"de", "en"}) {
		t.Errorf("got %v", got)
	}
}

func TestRankOrdersByLanguageThenRegularBeforeHearingImpaired(t *testing.T) {
	files := []Subtitle{
		file("en-sdh", "en", "a", 0, true),
		file("de-2", "de", "b", 1, false),
		file("fr", "fr", "c", 0, false),
		file("de-sdh", "de", "d", 0, true),
		file("de-1", "de", "e", 0, false),
		file("en-1", "en", "f", 3, false),
	}
	got := ids(Rank(files, []string{"de", "en"}))
	want := []string{"de-1", "de-2", "de-sdh", "en-1", "en-sdh"}
	if !equalStrings(got, want) {
		t.Errorf("got %v, want %v", got, want)
	}
}

func TestRankKeepsThreePerLanguage(t *testing.T) {
	files := []Subtitle{}
	for position := 0; position < 6; position++ {
		files = append(files, file(string(rune('a'+position)), "de", string(rune('a'+position)), position, false))
	}
	got := ids(Rank(files, []string{"de"}))
	if !equalStrings(got, []string{"a", "b", "c"}) {
		t.Errorf("got %v", got)
	}
}

func TestRankSkipsTheSameReleaseFromTwoPlugins(t *testing.T) {
	first := file("one", "de", "The.Matrix.1999.srt", 0, false)
	second := file("two", "de", "the matrix 1999", 0, false)
	second.Plugin = "zeta"
	third := file("three", "de", "Matrix.Reloaded", 1, false)
	got := ids(Rank([]Subtitle{second, first, third}, []string{"de"}))
	if !equalStrings(got, []string{"one", "three"}) {
		t.Errorf("got %v", got)
	}
}

func TestRankLabelsInTheLanguageItself(t *testing.T) {
	files := []Subtitle{
		file("1", "de", "a", 0, false),
		file("2", "de", "b", 1, false),
		file("3", "de", "c", 0, true),
		file("4", "en", "d", 0, true),
		file("5", "xx", "e", 0, false),
	}
	labels := []string{}
	for _, ranked := range Rank(files, []string{"de", "en", "xx"}) {
		labels = append(labels, ranked.Label)
	}
	want := []string{"Deutsch", "Deutsch 2", "Deutsch (SDH)", "English (SDH)", "XX"}
	if !equalStrings(labels, want) {
		t.Errorf("labels = %v, want %v", labels, want)
	}
}

func TestRankDropsLanguagesNobodyAskedFor(t *testing.T) {
	got := Rank([]Subtitle{file("fr", "fr", "a", 0, false)}, []string{"de"})
	if len(got) != 0 {
		t.Errorf("got %v", ids(got))
	}
}
