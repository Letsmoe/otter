package library

import (
	"context"
	"net/url"
	"slices"
	"strings"

	"github.com/neoworks/otter/internal/db"
)

// The host behind a link (voe.sx, dood.to, ...) mostly decides whether it
// plays and how fast, whichever site listed it. Every checked link updates
// its host's record, and links are raced best host first.

const (
	// Weight of the newest attempt in the moving averages.
	hosterSmoothing = 0.3
	// What a host without history is assumed to manage, so new hosts get tried.
	neutralReliability = 0.5
	unknownSpeedBps    = 300_000
)

// HosterStats is how one host's links did lately.
type HosterStats struct {
	Host        string  `json:"host"`
	Reliability float64 `json:"reliability"`
	SpeedBps    float64 `json:"speed_bps"`
	Attempts    int     `json:"attempts"`
}

// RecordHosterOutcome folds one checked link into its host's record. A
// played link without a measured speed (a local copy) leaves the speed alone.
func (s *Store) RecordHosterOutcome(ctx context.Context, linkURL string, played bool, speedBps float64) error {
	host := hostOfLink(linkURL)
	if host == "" {
		return nil
	}
	current, err := db.QueryOneAs[HosterStats](ctx, s.database, "SELECT * FROM hoster_stats WHERE host = $host", db.Vars{"host": host})
	if err != nil {
		return err
	}
	next := nextHosterStats(current, host, played, speedBps)
	_, err = s.database.Exec(ctx, `INSERT INTO hoster_stats (host, reliability, speed_bps, attempts, updated_at)
		VALUES ($host, $reliability, $speed_bps, $attempts, $now)
		ON CONFLICT (host) DO UPDATE SET reliability = excluded.reliability,
			speed_bps = excluded.speed_bps, attempts = excluded.attempts, updated_at = excluded.updated_at`,
		db.Vars{"host": host, "reliability": next.Reliability, "speed_bps": nullableNumber(next.SpeedBps),
			"attempts": next.Attempts, "now": db.Now()})
	return err
}

func nextHosterStats(current *HosterStats, host string, played bool, speedBps float64) HosterStats {
	next := HosterStats{Host: host, Reliability: neutralReliability}
	if current != nil {
		next = *current
	}
	outcome := 0.0
	if played {
		outcome = 1
	}
	next.Reliability = smooth(next.Reliability, outcome)
	next.Attempts++
	if played && speedBps > 0 {
		next.SpeedBps = smoothSpeed(next.SpeedBps, speedBps)
	}
	return next
}

func smooth(average float64, newest float64) float64 {
	return average*(1-hosterSmoothing) + newest*hosterSmoothing
}

// smoothSpeed starts from the first measurement instead of from zero.
func smoothSpeed(average float64, newest float64) float64 {
	if average <= 0 {
		return newest
	}
	return smooth(average, newest)
}

// AllHosterStats returns every host's record by host.
func (s *Store) AllHosterStats(ctx context.Context) (map[string]HosterStats, error) {
	rows, err := db.QueryAs[HosterStats](ctx, s.database, "SELECT * FROM hoster_stats", nil)
	if err != nil {
		return nil, err
	}
	byHost := make(map[string]HosterStats, len(rows))
	for _, row := range rows {
		byHost[row.Host] = row
	}
	return byHost, nil
}

// RankByHoster orders links by expected speed: the link's own measurement
// when it played before, else its host's average, times the host's
// reliability. Equal scores keep their order.
func RankByHoster(links []Stream, stats map[string]HosterStats) []Stream {
	ranked := slices.Clone(links)
	slices.SortStableFunc(ranked, func(first, second Stream) int {
		firstScore := expectedSpeed(first, stats)
		secondScore := expectedSpeed(second, stats)
		if firstScore > secondScore {
			return -1
		}
		if firstScore < secondScore {
			return 1
		}
		return 0
	})
	return ranked
}

func expectedSpeed(link Stream, stats map[string]HosterStats) float64 {
	reliability := neutralReliability
	speed := float64(unknownSpeedBps)
	hoster, known := stats[hostOfLink(link.URL)]
	if known {
		reliability = hoster.Reliability
	}
	if known && hoster.SpeedBps > 0 {
		speed = hoster.SpeedBps
	}
	playedBefore := link.Playable != nil && *link.Playable
	if playedBefore && link.SpeedBps > 0 {
		speed = link.SpeedBps
	}
	return reliability * speed
}

func hostOfLink(linkURL string) string {
	parsed, err := url.Parse(linkURL)
	if err != nil {
		return ""
	}
	return strings.TrimPrefix(strings.ToLower(parsed.Hostname()), "www.")
}
