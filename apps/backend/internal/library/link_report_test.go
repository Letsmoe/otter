package library

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/plugins"
)

func TestEffectiveHeightCountsLetterboxedFilmsByTheirWidth(t *testing.T) {
	cases := []struct{ width, height, want int }{
		{1920, 1080, 1080},
		{1920, 804, 1080},
		{1280, 536, 720},
		{1440, 1080, 1080},
		{0, 720, 720},
		{0, 0, 0},
	}
	for _, testCase := range cases {
		if got := EffectiveHeight(testCase.width, testCase.height); got != testCase.want {
			t.Errorf("EffectiveHeight(%d, %d) = %d, want %d", testCase.width, testCase.height, got, testCase.want)
		}
	}
}

func TestClaimedHeightReadsQualityThenLabel(t *testing.T) {
	cases := []struct {
		quality, label string
		want           int
	}{
		{"1080p", "", 1080},
		{"720P", "anything", 720},
		{"4K", "", 2160},
		{"HLS", "Movie 1080p WEB", 1080},
		{"", "Movie [UHD]", 2160},
		{"HLS", "Hoster", 0},
		{"", "", 0},
	}
	for _, testCase := range cases {
		if got := ClaimedHeight(testCase.quality, testCase.label); got != testCase.want {
			t.Errorf("ClaimedHeight(%q, %q) = %d, want %d", testCase.quality, testCase.label, got, testCase.want)
		}
	}
}

func TestOverclaimNeedsClearlyFewerPixels(t *testing.T) {
	if !isOverclaimed(1080, 720) {
		t.Error("720 delivered for a 1080 claim is an overclaim")
	}
	if isOverclaimed(1080, 1038) {
		t.Error("a few pixels short is not an overclaim")
	}
	if isOverclaimed(0, 480) || isOverclaimed(1080, 0) {
		t.Error("an unknown claim or measurement is no overclaim")
	}
}

func TestRoughPlaysStallOftenOrLong(t *testing.T) {
	if !isRoughPlay(PlaybackMeasurement{StallCount: 2, PlayedMillis: 600_000}) {
		t.Error("two stalls make a rough play")
	}
	if !isRoughPlay(PlaybackMeasurement{StallCount: 1, StallMillis: 40_000, PlayedMillis: 300_000}) {
		t.Error("a long stall is rough")
	}
	if isRoughPlay(PlaybackMeasurement{StallCount: 1, StallMillis: 2_000, PlayedMillis: 300_000}) {
		t.Error("one short stall is fine")
	}
}

func storeWithLink(t *testing.T, store *Store, url, quality string) int {
	t.Helper()
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	stream, err := store.AddStream(context.Background(), ref, "kinoking", plugins.Download{Label: "Movie", URL: url, Quality: quality})
	if err != nil {
		t.Fatal(err)
	}
	return stream.ID
}

func TestReportsAccumulatePerLinkAndSurviveAProbe(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	streamID := storeWithLink(t, store, "https://voe.sx/e/1", "1080p")
	// A probe replaces the quality with what it saw; the claim stays.
	if err := store.MarkPlayable(ctx, "https://voe.sx/e/1", true, 2_000_000, "720p"); err != nil {
		t.Fatal(err)
	}

	first, err := store.RecordLinkReport(ctx, streamID, PlaybackMeasurement{WidthPixels: 1280, HeightPixels: 536, BitrateBps: 2_500_000, FirstFrameMillis: 1200, PlayedMillis: 600_000})
	if err != nil {
		t.Fatal(err)
	}
	if first.ClaimedHeight != 1080 || first.MeasuredHeight != 720 || first.Overclaims != 1 || first.Plays != 1 {
		t.Fatalf("first report %+v", first)
	}

	second, err := store.RecordLinkReport(ctx, streamID, PlaybackMeasurement{WidthPixels: 1920, HeightPixels: 1080, StallCount: 3, StallMillis: 20_000, PlayedMillis: 600_000})
	if err != nil {
		t.Fatal(err)
	}
	if second.Plays != 2 || second.Overclaims != 1 || second.RoughPlays != 1 || second.MeasuredHeight != 1080 {
		t.Fatalf("second report %+v", second)
	}
}

func TestStreamsCarryTheirReport(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	streamID := storeWithLink(t, store, "https://voe.sx/e/1", "1080p")
	storeWithLink(t, store, "https://dood.to/e/2", "720p")
	if _, err := store.RecordLinkReport(ctx, streamID, PlaybackMeasurement{HeightPixels: 1080, PlayedMillis: 60_000}); err != nil {
		t.Fatal(err)
	}

	streams, err := store.Streams(ctx, MediaRef{MediaType: MediaTypeMovie, MediaID: 603})
	if err != nil {
		t.Fatal(err)
	}
	for _, stream := range streams {
		hasReport := stream.Report != nil
		if (stream.ID == streamID) != hasReport {
			t.Errorf("stream %d report = %+v", stream.ID, stream.Report)
		}
	}
}

func TestReportOfAnUnknownStreamIsRejected(t *testing.T) {
	store := newTestStore(t)
	if _, err := store.RecordLinkReport(context.Background(), 999, PlaybackMeasurement{}); err == nil {
		t.Fatal("expected an error")
	}
}

func TestStallingAndOverclaimingLinksAreTriedLater(t *testing.T) {
	smooth := &LinkReport{Plays: 4, RoughPlays: 0, FirstFrameMillis: 1500}
	stalling := &LinkReport{Plays: 4, RoughPlays: 4, FirstFrameMillis: 1500}
	overclaiming := &LinkReport{Plays: 4, Overclaims: 4, FirstFrameMillis: 1500}
	hoster := HosterStats{}
	if reportFactor(smooth, hoster) != 1 {
		t.Errorf("a smooth link keeps its score, factor %.2f", reportFactor(smooth, hoster))
	}
	if reportFactor(stalling, hoster) >= reportFactor(overclaiming, hoster) {
		t.Error("stalls cost more than a small overclaim")
	}
	if reportFactor(overclaiming, hoster) >= 1 {
		t.Error("overclaims cost score")
	}
}

func TestSlowStartCostsScore(t *testing.T) {
	quick := &LinkReport{Plays: 1, FirstFrameMillis: 1000}
	slow := &LinkReport{Plays: 1, FirstFrameMillis: 14_000}
	if reportFactor(slow, HosterStats{}) >= reportFactor(quick, HosterStats{}) {
		t.Error("a link that takes long to start should score lower")
	}
}

func TestAnUnplayedLinkBorrowsItsHostsRecordAtHalfWeight(t *testing.T) {
	roughHost := HosterStats{Reports: 10, RoughShare: 1}
	if got := reportFactor(nil, roughHost); got != 1-roughPenalty/2 {
		t.Errorf("factor %.2f, want %.2f", got, 1-roughPenalty/2)
	}
	fewReports := HosterStats{Reports: 1, RoughShare: 1}
	if reportFactor(nil, fewReports) != 1 {
		t.Error("a host with barely any reports says nothing")
	}
}

func TestRankByHosterTriesTheSmoothLinkBeforeTheStallingOne(t *testing.T) {
	stalling := Stream{ID: 1, URL: "https://voe.sx/e/1", Report: &LinkReport{Plays: 3, RoughPlays: 3, FirstFrameMillis: 1000}}
	smooth := Stream{ID: 2, URL: "https://voe.sx/e/2", Report: &LinkReport{Plays: 3, FirstFrameMillis: 1000}}
	stats := map[string]HosterStats{"voe.sx": {Host: "voe.sx", Reliability: 0.9, SpeedBps: 2_000_000}}

	ranked := RankByHoster([]Stream{stalling, smooth}, stats)
	if ranked[0].ID != 2 {
		t.Fatalf("ranked %d first, want the smooth link", ranked[0].ID)
	}
}

func TestHostReportsReachTheHosterStats(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	streamID := storeWithLink(t, store, "https://voe.sx/e/1", "1080p")
	if err := store.RecordHosterOutcome(ctx, "https://voe.sx/e/1", true, 1_000_000); err != nil {
		t.Fatal(err)
	}
	if _, err := store.RecordLinkReport(ctx, streamID, PlaybackMeasurement{HeightPixels: 480, StallCount: 4, PlayedMillis: 100_000}); err != nil {
		t.Fatal(err)
	}

	stats, err := store.AllHosterStats(ctx)
	if err != nil {
		t.Fatal(err)
	}
	voe := stats["voe.sx"]
	if voe.Reports != 1 || voe.RoughShare != 1 || voe.OverclaimShare != 1 {
		t.Fatalf("voe %+v", voe)
	}
}
