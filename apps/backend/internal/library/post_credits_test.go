package library

import "testing"

func TestHasPostCreditsScene(t *testing.T) {
	cases := []struct {
		name     string
		keywords []string
		want     bool
	}{
		{"no keywords", nil, false},
		{"unrelated keywords", []string{"superhero", "sequel"}, false},
		{"scene during the credits", []string{"superhero", "duringcreditsstinger"}, true},
		{"scene after the credits", []string{"aftercreditsstinger"}, true},
		{"spelling of the keyword varies", []string{" AfterCreditsStinger "}, true},
		{"only part of the keyword", []string{"credits"}, false},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			title := Title{Keywords: testCase.keywords}
			if got := title.HasPostCreditsScene(); got != testCase.want {
				t.Fatalf("HasPostCreditsScene() = %v, want %v", got, testCase.want)
			}
		})
	}
}
