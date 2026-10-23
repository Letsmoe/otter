package subtitles

import "strings"

// languageNames are the languages' own names, as the apps show them.
var languageNames = map[string]string{
	"en": "English", "de": "Deutsch", "fr": "Français", "es": "Español", "it": "Italiano",
	"pt": "Português", "nl": "Nederlands", "pl": "Polski", "ru": "Русский", "tr": "Türkçe",
	"sv": "Svenska", "da": "Dansk", "no": "Norsk", "fi": "Suomi", "cs": "Čeština",
	"sk": "Slovenčina", "hu": "Magyar", "ro": "Română", "bg": "Български", "el": "Ελληνικά",
	"he": "עברית", "ar": "العربية", "zh": "中文", "ja": "日本語", "ko": "한국어",
	"hi": "हिन्दी", "th": "ไทย", "vi": "Tiếng Việt", "id": "Bahasa Indonesia", "uk": "Українська",
	"hr": "Hrvatski", "sr": "Српски", "sl": "Slovenščina", "lt": "Lietuvių", "lv": "Latviešu",
	"et": "Eesti", "fa": "فارسی", "ca": "Català", "eu": "Euskara", "gl": "Galego",
	"is": "Íslenska", "ms": "Bahasa Melayu", "bn": "বাংলা", "ta": "தமிழ்", "sq": "Shqip",
	"mk": "Македонски", "bs": "Bosanski", "hy": "Հայերեն", "ka": "ქართული",
}

// languageName is the display name of an ISO 639-1 code; unknown codes show
// in capitals.
func languageName(code string) string {
	name, known := languageNames[code]
	if known {
		return name
	}
	return strings.ToUpper(code)
}
