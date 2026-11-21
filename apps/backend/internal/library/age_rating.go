package library

import (
	"regexp"
	"strconv"
	"strings"
)

// minimumAges maps each country's certifications to the age they are for.
// Countries not listed (and certifications not listed for a listed country)
// fall back to the number in the certification, e.g. "12+" or "FSK 16".
var minimumAges = map[string]map[string]int{
	// Germany: FSK.
	"DE": {"0": 0, "6": 6, "12": 12, "16": 16, "18": 18},
	"US": {
		"G": 0, "PG": 8, "PG-13": 13, "R": 17, "NC-17": 18,
		"TV-Y": 0, "TV-G": 0, "TV-Y7": 7, "TV-PG": 10, "TV-14": 14, "TV-MA": 17,
	},
	"GB": {"U": 0, "UC": 0, "PG": 8, "12": 12, "12A": 12, "15": 15, "18": 18, "R18": 18},
	"FR": {"U": 0, "TP": 0, "10": 10, "12": 12, "16": 16, "18": 18},
	"ES": {"A": 0, "APTA": 0, "7": 7, "12": 12, "16": 16, "18": 18},
	"NL": {"AL": 0, "6": 6, "9": 9, "12": 12, "14": 14, "16": 16, "18": 18},
	"IT": {"T": 0, "6": 6, "14": 14, "18": 18},
	"AU": {"G": 0, "PG": 8, "M": 15, "MA15+": 15, "R18+": 18},
	"BR": {"L": 0, "10": 10, "12": 12, "14": 14, "16": 16, "18": 18},
}

var numberInCertification = regexp.MustCompile(`\d+`)

// countryOfLanguage is the country a language's speakers most likely are in,
// when the profile names no region.
var countryOfLanguage = map[string]string{
	"de": "DE", "en": "US", "fr": "FR", "es": "ES", "it": "IT", "nl": "NL", "pt": "BR",
}

// MinimumAge is the youngest age a certification of [country] allows; false
// when it can't be read.
func MinimumAge(country, certification string) (int, bool) {
	normalized := strings.ToUpper(strings.TrimSpace(certification))
	if normalized == "" {
		return 0, false
	}
	if age, known := minimumAges[strings.ToUpper(country)][normalized]; known {
		return age, true
	}
	number := numberInCertification.FindString(normalized)
	if number == "" {
		return 0, false
	}
	age, err := strconv.Atoi(number)
	if err != nil || age > 21 {
		return 0, false
	}
	return age, true
}

// CountryFor picks the country whose ratings apply to a profile: the region of
// the TMDB language when it is the profile's first language ("de-DE" for a
// German profile), else the country its first language is spoken in.
func CountryFor(profileLanguages []string, tmdbLanguage string) string {
	if len(profileLanguages) == 0 {
		return regionOf(tmdbLanguage)
	}
	first := strings.ToLower(profileLanguages[0])
	language, region, _ := strings.Cut(tmdbLanguage, "-")
	if strings.ToLower(language) == first && region != "" {
		return strings.ToUpper(region)
	}
	return countryOfLanguage[first]
}

func regionOf(tmdbLanguage string) string {
	_, region, _ := strings.Cut(tmdbLanguage, "-")
	return strings.ToUpper(region)
}
