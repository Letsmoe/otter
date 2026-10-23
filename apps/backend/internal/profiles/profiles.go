// Package profiles manages the people using one otter install and what each
// of them did: watch progress, ratings and watchlist.
package profiles

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"regexp"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/hooks"
)

const (
	pinLockPrefix      = "pin:lock:"
	pinFailCountPrefix = "pin:fails:"
	pinFailCountTTL    = 24 * time.Hour
	maxPinWaitSeconds  = 30 * 60
)

var (
	ErrProfileNotFound = errors.New("profile not found")
	ErrNameTaken       = errors.New("a profile with this name already exists")
)

// storeError turns SQLite's unique-name violation into ErrNameTaken, so the
// client can show it as is.
func storeError(action string, err error) error {
	if strings.Contains(err.Error(), "UNIQUE constraint failed: profile.name") {
		return ErrNameTaken
	}
	return fmt.Errorf("%s profile: %w", action, err)
}

type Profile struct {
	ID        int    `json:"id"`
	Name      string `json:"name"`
	AvatarURL string `json:"avatar_url"`
	PinHash   string `json:"pin_hash"`
	IsChild   bool   `json:"is_child"`
	CreatedAt string `json:"created_at"`
	// Languages is a JSON array of ISO 639-1 codes, most preferred first.
	Languages string `json:"languages"`
	// SubtitlesEnabled shows captions by default.
	SubtitlesEnabled bool `json:"subtitles_enabled"`
	// SubtitleLanguage is an ISO 639-1 code; empty means the first language.
	SubtitleLanguage string `json:"subtitle_language"`
	// SubtitleSize is one of SubtitleSizes.
	SubtitleSize string `json:"subtitle_size"`
	// VoiceBoost lifts dialogue over music and effects.
	VoiceBoost bool `json:"voice_boost"`
}

// Caption sizes a profile can pick.
const (
	SubtitleSizeSmall  = "small"
	SubtitleSizeMedium = "medium"
	SubtitleSizeLarge  = "large"
)

// LanguageList returns the profile's audio languages, most preferred first.
func (p Profile) LanguageList() []string {
	var languages []string
	if err := json.Unmarshal([]byte(p.Languages), &languages); err != nil {
		return []string{}
	}
	return languages
}

// CaptionLanguage is the language captions are shown in by default: the
// chosen one, else the first audio language.
func (p Profile) CaptionLanguage() string {
	if p.SubtitleLanguage != "" {
		return p.SubtitleLanguage
	}
	languages := p.LanguageList()
	if len(languages) == 0 {
		return ""
	}
	return languages[0]
}

// HasPin reports whether switching to the profile needs a PIN.
func (p Profile) HasPin() bool {
	return p.PinHash != ""
}

// PinCheck is the outcome of a PIN attempt. After a failure, WaitSeconds is
// how long further attempts are refused.
type PinCheck struct {
	Success     bool
	WaitSeconds int
}

type Service struct {
	database *db.DB
	cache    *cache.Client
	hooks    *hooks.Runner
}

func NewService(database *db.DB, cacheClient *cache.Client, hookRunner *hooks.Runner) *Service {
	return &Service{database: database, cache: cacheClient, hooks: hookRunner}
}

func (s *Service) List(ctx context.Context) ([]Profile, error) {
	return db.QueryAs[Profile](ctx, s.database, "SELECT * FROM profile ORDER BY created_at", nil)
}

func (s *Service) Get(ctx context.Context, profileID int) (*Profile, error) {
	profile, err := db.QueryOneAs[Profile](ctx, s.database, "SELECT * FROM profile WHERE id = $id",
		db.Vars{"id": profileID})
	if err != nil {
		return nil, err
	}
	if profile == nil {
		return nil, ErrProfileNotFound
	}
	return profile, nil
}

// Create adds a profile. An empty pin means no PIN.
func (s *Service) Create(ctx context.Context, name, avatarURL, pin string, isChild bool) (*Profile, error) {
	pinHash, err := hashPin(pin)
	if err != nil {
		return nil, err
	}
	row, err := s.database.QueryRow(ctx, `INSERT INTO profile (name, avatar_url, pin_hash, is_child, created_at)
		VALUES ($name, $avatar_url, $pin_hash, $is_child, $now) RETURNING id`,
		db.Vars{"name": name, "avatar_url": avatarURL, "pin_hash": pinHash, "is_child": isChild, "now": db.Now()})
	if err != nil {
		return nil, storeError("create", err)
	}
	return s.Get(ctx, db.AsInt(row["id"]))
}

// Update holds the profile fields to change; nil fields stay as they are.
// An empty Pin removes the PIN.
type Update struct {
	Name             *string
	AvatarURL        *string
	IsChild          *bool
	Pin              *string
	Languages        *[]string
	SubtitlesEnabled *bool
	// SubtitleLanguage "" clears the choice.
	SubtitleLanguage *string
	SubtitleSize     *string
	VoiceBoost       *bool
}

// Update changes a profile's fields and returns the stored result.
func (s *Service) Update(ctx context.Context, profileID int, update Update) (*Profile, error) {
	profile, err := s.Get(ctx, profileID)
	if err != nil {
		return nil, err
	}
	if err := applyUpdate(profile, update); err != nil {
		return nil, err
	}
	_, err = s.database.Exec(ctx, `UPDATE profile
		SET name = $name, avatar_url = $avatar_url, pin_hash = $pin_hash, is_child = $is_child,
			languages = $languages, subtitles_enabled = $subtitles_enabled,
			subtitle_language = $subtitle_language, subtitle_size = $subtitle_size, voice_boost = $voice_boost
		WHERE id = $id`,
		db.Vars{"id": profileID, "name": profile.Name, "avatar_url": profile.AvatarURL,
			"pin_hash": profile.PinHash, "is_child": profile.IsChild, "languages": profile.Languages,
			"subtitles_enabled": profile.SubtitlesEnabled, "subtitle_language": nullableText(profile.SubtitleLanguage),
			"subtitle_size": profile.SubtitleSize, "voice_boost": profile.VoiceBoost})
	if err != nil {
		return nil, storeError("update", err)
	}
	return s.Get(ctx, profileID)
}

func applyUpdate(profile *Profile, update Update) error {
	if update.Name != nil {
		profile.Name = *update.Name
	}
	if update.AvatarURL != nil {
		profile.AvatarURL = *update.AvatarURL
	}
	if update.IsChild != nil {
		profile.IsChild = *update.IsChild
	}
	if update.Languages != nil {
		languages, err := encodeLanguages(*update.Languages)
		if err != nil {
			return err
		}
		profile.Languages = languages
	}
	if err := applyCaptionUpdate(profile, update); err != nil {
		return err
	}
	if update.Pin == nil {
		return nil
	}
	pinHash, err := hashPin(*update.Pin)
	if err != nil {
		return err
	}
	profile.PinHash = pinHash
	return nil
}

func applyCaptionUpdate(profile *Profile, update Update) error {
	if update.SubtitlesEnabled != nil {
		profile.SubtitlesEnabled = *update.SubtitlesEnabled
	}
	if update.VoiceBoost != nil {
		profile.VoiceBoost = *update.VoiceBoost
	}
	if update.SubtitleSize != nil {
		if err := validateSubtitleSize(*update.SubtitleSize); err != nil {
			return err
		}
		profile.SubtitleSize = *update.SubtitleSize
	}
	if update.SubtitleLanguage == nil {
		return nil
	}
	language, err := normalizeCaptionLanguage(*update.SubtitleLanguage)
	if err != nil {
		return err
	}
	profile.SubtitleLanguage = language
	return nil
}

func validateSubtitleSize(size string) error {
	switch size {
	case SubtitleSizeSmall, SubtitleSizeMedium, SubtitleSizeLarge:
		return nil
	}
	return fmt.Errorf("%q is not a caption size", size)
}

// normalizeCaptionLanguage validates an ISO 639-1 code; "" stays empty.
func normalizeCaptionLanguage(language string) (string, error) {
	code := strings.ToLower(strings.TrimSpace(language))
	if code == "" {
		return "", nil
	}
	if !languageCode.MatchString(code) {
		return "", fmt.Errorf("%q is not a two-letter language code", language)
	}
	return code, nil
}

func nullableText(value string) any {
	if value == "" {
		return nil
	}
	return value
}

// Delete removes a profile and everything it recorded.
func (s *Service) Delete(ctx context.Context, profileID int) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		for _, table := range []string{"watch_history", "user_rating", "watchlist"} {
			if _, err := tx.Exec(ctx, "DELETE FROM "+table+" WHERE profile_id = $id", db.Vars{"id": profileID}); err != nil {
				return err
			}
		}
		_, err := tx.Exec(ctx, "DELETE FROM profile WHERE id = $id", db.Vars{"id": profileID})
		return err
	})
}

func hashPin(pin string) (string, error) {
	if pin == "" {
		return "", nil
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(pin), bcrypt.DefaultCost)
	if err != nil {
		return "", fmt.Errorf("hash pin: %w", err)
	}
	return string(hash), nil
}

// VerifyPin checks a profile's PIN. Each failure doubles the lockout, up to
// 30 minutes; a success clears it.
func (s *Service) VerifyPin(ctx context.Context, profileID int, pin string) (PinCheck, error) {
	key := strconv.Itoa(profileID)
	if wait := s.lockoutSeconds(ctx, key); wait > 0 {
		return PinCheck{WaitSeconds: wait}, nil
	}
	profile, err := s.Get(ctx, profileID)
	if err != nil {
		return PinCheck{}, err
	}
	if !profile.HasPin() || bcrypt.CompareHashAndPassword([]byte(profile.PinHash), []byte(pin)) == nil {
		_ = s.cache.Del(ctx, pinLockPrefix+key, pinFailCountPrefix+key)
		return PinCheck{Success: true}, nil
	}
	failures, _ := s.cache.Incr(ctx, pinFailCountPrefix+key, pinFailCountTTL)
	waitSeconds := int(math.Min(math.Pow(2, float64(failures)), maxPinWaitSeconds))
	_ = s.cache.Set(ctx, pinLockPrefix+key, "1", time.Duration(waitSeconds)*time.Second)
	return PinCheck{WaitSeconds: waitSeconds}, nil
}

func (s *Service) lockoutSeconds(ctx context.Context, key string) int {
	remaining, err := s.cache.TTL(ctx, pinLockPrefix+key)
	if err != nil || remaining <= 0 {
		return 0
	}
	return int(math.Ceil(remaining.Seconds()))
}

var languageCode = regexp.MustCompile(`^[a-z]{2}$`)

// encodeLanguages validates ISO 639-1 codes and stores them without duplicates.
func encodeLanguages(languages []string) (string, error) {
	seen := map[string]bool{}
	cleaned := []string{}
	for _, language := range languages {
		code := strings.ToLower(strings.TrimSpace(language))
		if !languageCode.MatchString(code) {
			return "", fmt.Errorf("%q is not a two-letter language code", language)
		}
		if seen[code] {
			continue
		}
		seen[code] = true
		cleaned = append(cleaned, code)
	}
	if len(cleaned) == 0 {
		return "", errors.New("a profile needs at least one language")
	}
	encoded, err := json.Marshal(cleaned)
	return string(encoded), err
}
