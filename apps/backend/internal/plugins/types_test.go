package plugins

import "testing"

func TestServesURL(t *testing.T) {
	plugin := &Plugin{Meta: PluginMeta{Domains: []string{"voe.sx", "*"}}}
	cases := map[string]bool{
		"https://www.voe.sx/e/abc123":    true,
		"https://static.voe.sx/logo.png": true,
		"https://notvoe.sx/":             false,
		"https://streamtape.com/e/abc":   false,
		"not a url":                      false,
	}
	for rawURL, want := range cases {
		if got := plugin.ServesURL(rawURL); got != want {
			t.Errorf("ServesURL(%q) = %v, want %v", rawURL, got, want)
		}
	}
}
