package library

import (
	"context"
	"testing"
)

func TestHosterRecordsFollowRecentAttempts(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	for range 3 {
		if err := store.RecordHosterOutcome(ctx, "https://voe.sx/e/1", true, 2_000_000); err != nil {
			t.Fatal(err)
		}
	}
	if err := store.RecordHosterOutcome(ctx, "https://www.dood.to/e/1", false, 0); err != nil {
		t.Fatal(err)
	}
	stats, err := store.AllHosterStats(ctx)
	if err != nil {
		t.Fatal(err)
	}
	voe := stats["voe.sx"]
	if voe.Attempts != 3 || voe.Reliability <= neutralReliability || voe.SpeedBps != 2_000_000 {
		t.Fatalf("voe %+v", voe)
	}
	if dood := stats["dood.to"]; dood.Reliability >= neutralReliability || dood.SpeedBps != 0 {
		t.Fatalf("dood %+v", dood)
	}
}

func TestHosterReliabilityRecoversAndDrops(t *testing.T) {
	stats := HosterStats{Host: "voe.sx", Reliability: 0.9, SpeedBps: 1_000_000}
	for range 4 {
		stats = nextHosterStats(&stats, "voe.sx", false, 0)
	}
	if stats.Reliability > 0.25 {
		t.Fatalf("four failures in a row leave reliability at %.2f", stats.Reliability)
	}
	stats = nextHosterStats(&stats, "voe.sx", true, 3_000_000)
	if stats.SpeedBps <= 1_000_000 || stats.SpeedBps >= 3_000_000 {
		t.Fatalf("speed %.0f should move toward the new measurement", stats.SpeedBps)
	}
}

func TestRankByHosterPutsReliableFastHostsFirst(t *testing.T) {
	playedFast := true
	links := []Stream{
		{ID: 1, URL: "https://dood.to/e/1"},
		{ID: 2, URL: "https://new-host.example/e/1"},
		{ID: 3, URL: "https://voe.sx/e/1"},
		{ID: 4, URL: "https://dood.to/e/2", Playable: &playedFast, SpeedBps: 9_000_000},
	}
	stats := map[string]HosterStats{
		"voe.sx":  {Host: "voe.sx", Reliability: 0.95, SpeedBps: 2_500_000},
		"dood.to": {Host: "dood.to", Reliability: 0.2, SpeedBps: 1_000_000},
	}
	ranked := RankByHoster(links, stats)
	// voe 0.95 x 2.5M; dood.to/e/2 played at 9 MB/s itself: 0.2 x 9M; the other
	// dood link 0.2 x 1M; the unknown host the neutral 0.5 x 300K.
	want := []int{3, 4, 1, 2}
	for index, id := range want {
		if ranked[index].ID != id {
			t.Fatalf("ranked %v, want ids %v", idsOf(ranked), want)
		}
	}
}

func idsOf(links []Stream) []int {
	ids := make([]int, len(links))
	for index, link := range links {
		ids[index] = link.ID
	}
	return ids
}
