package settings

// tmdbRegions picks the region TMDB localizes a language for, so titles and
// release dates match the apps' language list (pt-BR, not pt-PT).
var tmdbRegions = map[string]string{
	"de": "DE", "en": "US", "es": "ES", "fr": "FR", "it": "IT",
	"ja": "JP", "nl": "NL", "pl": "PL", "pt": "BR", "tr": "TR",
}

// TMDBLanguageFor turns a profile's ISO 639-1 code into a TMDB language
// ("de" → "de-DE"); codes without a known region are passed on as they are.
func TMDBLanguageFor(languageCode string) string {
	region, found := tmdbRegions[languageCode]
	if !found {
		return languageCode
	}
	return languageCode + "-" + region
}
