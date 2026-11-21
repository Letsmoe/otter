package library

import (
	"context"
	"errors"
	"regexp"
	"strconv"
	"strings"

	"github.com/Letsmoe/otter/internal/db"
)

// A link's label says what the plugin hopes for; the app measures what the
// link delivered when it played. Reports are kept per link and used to try
// links that deliver what they claim, without stalls, first.

const (
	// A picture with less than this share of the claimed height counts as
	// overclaimed (1080p claimed, 720p delivered).
	overclaimShare = 0.85
	// More stalls than this, or this share of the play spent stalled, is a rough play.
	roughStallCount = 2
	roughStallShare = 0.05
	// Plays of other links on the host before its record counts for a link without one.
	minHostReports = 3
	// The score a link that always stalls keeps, and one that always overclaims.
	roughPenalty     = 0.6
	overclaimPenalty = 0.3
	// Time to the first picture beyond this makes a link less attractive.
	slowStartMillis = 4000
	slowStartScale  = 20000.0
	// Sanity bounds on what a client sends.
	maxReportedHeight = 8640
	maxReportedMillis = 24 * 60 * 60 * 1000
)

// LinkReport is what the app measured while a link played, over all plays.
type LinkReport struct {
	URL  string `json:"url"`
	Host string `json:"host"`
	// ClaimedHeight is what the plugin's label named, MeasuredHeight what the picture had; 0 when unknown.
	ClaimedHeight  int `json:"claimed_height"`
	MeasuredHeight int `json:"measured_height"`
	// The latest play.
	BitrateBps       int `json:"bitrate_bps"`
	StallCount       int `json:"stall_count"`
	StallMillis      int `json:"stall_millis"`
	FirstFrameMillis int `json:"first_frame_millis"`
	PlayedMillis     int `json:"played_millis"`
	// Over all plays.
	Plays      int    `json:"plays"`
	Overclaims int    `json:"overclaims"`
	RoughPlays int    `json:"rough_plays"`
	UpdatedAt  string `json:"updated_at"`
}

// Overclaimed reports whether the latest play had clearly fewer pixels than claimed.
func (r LinkReport) Overclaimed() bool {
	return isOverclaimed(r.ClaimedHeight, r.MeasuredHeight)
}

// PlaybackMeasurement is what a client measured during one play of a link.
type PlaybackMeasurement struct {
	WidthPixels      int
	HeightPixels     int
	BitrateBps       int
	StallCount       int
	StallMillis      int
	FirstFrameMillis int
	PlayedMillis     int
}

// EffectiveHeight is the pixel height a picture counts as: wide films are
// letterboxed (1920x804 is a 1080p release), so the width decides when it is larger.
func EffectiveHeight(width, height int) int {
	fromWidth := width * 9 / 16
	if fromWidth > height {
		return fromWidth
	}
	return height
}

var pixelHeightPattern = regexp.MustCompile(`(?i)\b(\d{3,4})\s?p\b`)

// ClaimedHeight reads the resolution a link claims from its quality, else
// from its label ("1080p", "4K"); 0 when it names none.
func ClaimedHeight(quality, label string) int {
	if height := heightIn(quality); height > 0 {
		return height
	}
	return heightIn(label)
}

func heightIn(text string) int {
	lowered := strings.ToLower(text)
	if strings.Contains(lowered, "4k") || strings.Contains(lowered, "uhd") {
		return 2160
	}
	match := pixelHeightPattern.FindStringSubmatch(text)
	if match == nil {
		return 0
	}
	height, _ := strconv.Atoi(match[1])
	return height
}

func isOverclaimed(claimedHeight, measuredHeight int) bool {
	if claimedHeight <= 0 || measuredHeight <= 0 {
		return false
	}
	return float64(measuredHeight) < float64(claimedHeight)*overclaimShare
}

func isRoughPlay(measurement PlaybackMeasurement) bool {
	if measurement.StallCount >= roughStallCount {
		return true
	}
	if measurement.PlayedMillis <= 0 {
		return false
	}
	return float64(measurement.StallMillis) > float64(measurement.PlayedMillis)*roughStallShare
}

// cleanMeasurement drops values a client could not have measured.
func cleanMeasurement(measurement PlaybackMeasurement) PlaybackMeasurement {
	measurement.WidthPixels = clampInt(measurement.WidthPixels, 0, maxReportedHeight*2)
	measurement.HeightPixels = clampInt(measurement.HeightPixels, 0, maxReportedHeight)
	measurement.BitrateBps = clampInt(measurement.BitrateBps, 0, 1_000_000_000)
	measurement.StallCount = clampInt(measurement.StallCount, 0, 10_000)
	measurement.StallMillis = clampInt(measurement.StallMillis, 0, maxReportedMillis)
	measurement.FirstFrameMillis = clampInt(measurement.FirstFrameMillis, 0, maxReportedMillis)
	measurement.PlayedMillis = clampInt(measurement.PlayedMillis, 0, maxReportedMillis)
	return measurement
}

func clampInt(value, low, high int) int {
	return max(low, min(value, high))
}

// RecordLinkReport folds one play of a stored link into its report and
// returns the report. The claim is read from the stream, which keeps what the
// plugin said when a probe later replaced its quality.
func (s *Store) RecordLinkReport(ctx context.Context, streamID int, measurement PlaybackMeasurement) (*LinkReport, error) {
	stream, err := s.Stream(ctx, streamID)
	if err != nil {
		return nil, err
	}
	if stream == nil {
		return nil, errors.New("stream not found")
	}
	measurement = cleanMeasurement(measurement)
	claimed := ClaimedHeight(stream.ClaimedQuality, stream.Label)
	measured := EffectiveHeight(measurement.WidthPixels, measurement.HeightPixels)
	vars := db.Vars{
		"url": stream.URL, "host": hostOfLink(stream.URL), "claimed": claimed, "measured": measured,
		"bitrate": measurement.BitrateBps, "stall_count": measurement.StallCount,
		"stall_millis": measurement.StallMillis, "first_frame": measurement.FirstFrameMillis,
		"played": measurement.PlayedMillis, "overclaim": boolToInt(isOverclaimed(claimed, measured)),
		"rough": boolToInt(isRoughPlay(measurement)), "now": db.Now(),
	}
	_, err = s.database.Exec(ctx, `INSERT INTO link_report (url, host, claimed_height, measured_height,
		bitrate_bps, stall_count, stall_millis, first_frame_millis, played_millis,
		plays, overclaims, rough_plays, updated_at)
	VALUES ($url, $host, $claimed, $measured, $bitrate, $stall_count, $stall_millis, $first_frame,
		$played, 1, $overclaim, $rough, $now)
	ON CONFLICT (url) DO UPDATE SET claimed_height = excluded.claimed_height,
		measured_height = excluded.measured_height, bitrate_bps = excluded.bitrate_bps,
		stall_count = excluded.stall_count, stall_millis = excluded.stall_millis,
		first_frame_millis = excluded.first_frame_millis, played_millis = excluded.played_millis,
		plays = link_report.plays + 1, overclaims = link_report.overclaims + $overclaim,
		rough_plays = link_report.rough_plays + $rough, updated_at = excluded.updated_at`, vars)
	if err != nil {
		return nil, err
	}
	return db.QueryOneAs[LinkReport](ctx, s.database, "SELECT * FROM link_report WHERE url = $url", vars)
}

func boolToInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

// AttachReports sets each stream's report, when its link has one.
func (s *Store) AttachReports(ctx context.Context, streams []Stream) error {
	if len(streams) == 0 {
		return nil
	}
	urls := make([]string, 0, len(streams))
	for _, stream := range streams {
		urls = append(urls, stream.URL)
	}
	reports, err := db.QueryAs[LinkReport](ctx, s.database,
		"SELECT * FROM link_report WHERE url IN (SELECT value FROM json_each($urls))", db.Vars{"urls": urls})
	if err != nil {
		return err
	}
	byURL := make(map[string]LinkReport, len(reports))
	for _, report := range reports {
		byURL[report.URL] = report
	}
	for index := range streams {
		report, found := byURL[streams[index].URL]
		if found {
			streams[index].Report = &report
		}
	}
	return nil
}

// hosterReports adds what the app reported about each host's links to the hosts' stats.
func (s *Store) hosterReports(ctx context.Context, stats map[string]HosterStats) error {
	rows, err := s.database.Query(ctx, `SELECT host, SUM(plays) AS plays, SUM(overclaims) AS overclaims,
		SUM(rough_plays) AS rough_plays FROM link_report GROUP BY host`, nil)
	if err != nil {
		return err
	}
	for _, row := range rows {
		host, _ := row["host"].(string)
		hoster, known := stats[host]
		if !known {
			continue
		}
		hoster.Reports = db.AsInt(row["plays"])
		hoster.OverclaimShare = shareOf(db.AsInt(row["overclaims"]), hoster.Reports)
		hoster.RoughShare = shareOf(db.AsInt(row["rough_plays"]), hoster.Reports)
		stats[host] = hoster
	}
	return nil
}

func shareOf(part, whole int) float64 {
	if whole <= 0 {
		return 0
	}
	return float64(part) / float64(whole)
}

// reportFactor scales a link's expected speed by how its plays went: a link
// that stalled, delivered less than it claimed or took long to start keeps
// less of its score. A link nobody played falls back on its host's record,
// at half the weight.
func reportFactor(report *LinkReport, hoster HosterStats) float64 {
	if report != nil && report.Plays > 0 {
		rough := shareOf(report.RoughPlays, report.Plays)
		overclaimed := shareOf(report.Overclaims, report.Plays)
		return penalty(rough, overclaimed) * startFactor(report.FirstFrameMillis)
	}
	if hoster.Reports >= minHostReports {
		return penalty(hoster.RoughShare/2, hoster.OverclaimShare/2)
	}
	return 1
}

func penalty(roughShare, overclaimedShare float64) float64 {
	return (1 - roughPenalty*roughShare) * (1 - overclaimPenalty*overclaimedShare)
}

func startFactor(firstFrameMillis int) float64 {
	if firstFrameMillis <= slowStartMillis {
		return 1
	}
	return 1 / (1 + float64(firstFrameMillis-slowStartMillis)/slowStartScale)
}
