package resolve

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/plugins"
)

type fakeLink struct {
	delay          time.Duration
	bytesPerSecond float64
	cached         bool
	fails          bool
}

// fakeTrier answers each link after its delay; it records which links were tried.
func fakeTrier(links map[string]fakeLink) (linkTrier, func() []string) {
	var mutex sync.Mutex
	var tried []string
	trier := func(ctx context.Context, link string) Outcome {
		mutex.Lock()
		tried = append(tried, link)
		mutex.Unlock()
		behaviour := links[link]
		select {
		case <-time.After(behaviour.delay):
		case <-ctx.Done():
			return Outcome{URL: link, Err: ctx.Err()}
		}
		if behaviour.fails {
			return Outcome{URL: link, Err: errors.New("dead")}
		}
		result := &plugins.ResolveResult{URL: link + "/media", MimeType: "video/mp4"}
		return Outcome{URL: link, Result: result, Cached: behaviour.cached, BytesPerSecond: behaviour.bytesPerSecond}
	}
	triedLinks := func() []string {
		mutex.Lock()
		defer mutex.Unlock()
		return append([]string(nil), tried...)
	}
	return trier, triedLinks
}

func withRaceTiming(t *testing.T, decision time.Duration) {
	t.Helper()
	previous := raceDecisionDelay
	raceDecisionDelay = decision
	t.Cleanup(func() { raceDecisionDelay = previous })
}

func ignoreOutcome(Outcome) {}

func TestPickBestTakesTheFastestPlayingLink(t *testing.T) {
	withRaceTiming(t, 50*time.Millisecond)
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://a.example/1": {delay: 5 * time.Millisecond, bytesPerSecond: 100},
		"https://b.example/1": {delay: 10 * time.Millisecond, bytesPerSecond: 900},
		"https://c.example/1": {delay: 1 * time.Millisecond, fails: true},
	})
	winner, err := pickBest(context.Background(), []string{"https://a.example/1", "https://b.example/1", "https://c.example/1"}, trier, ignoreOutcome)
	if err != nil {
		t.Fatal(err)
	}
	if winner.URL != "https://b.example/1" {
		t.Fatalf("winner %s", winner.URL)
	}
}

func TestPickBestDoesNotWaitForSlowLinksAfterTheDecision(t *testing.T) {
	withRaceTiming(t, 30*time.Millisecond)
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://quick.example/1": {delay: 5 * time.Millisecond, bytesPerSecond: 100},
		"https://slow.example/1":  {delay: 5 * time.Second, bytesPerSecond: 9000},
	})
	started := time.Now()
	winner, err := pickBest(context.Background(), []string{"https://quick.example/1", "https://slow.example/1"}, trier, ignoreOutcome)
	if err != nil || winner.URL != "https://quick.example/1" {
		t.Fatalf("winner %s, err %v", winner.URL, err)
	}
	if time.Since(started) > time.Second {
		t.Fatalf("waited %s for the slow link", time.Since(started))
	}
}

func TestPickBestTakesTheFirstPlayingLinkAfterTheDecisionWhenNoneWasReady(t *testing.T) {
	withRaceTiming(t, 10*time.Millisecond)
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://late.example/1":  {delay: 60 * time.Millisecond, bytesPerSecond: 50},
		"https://later.example/1": {delay: 5 * time.Second, bytesPerSecond: 9000},
	})
	winner, err := pickBest(context.Background(), []string{"https://late.example/1", "https://later.example/1"}, trier, ignoreOutcome)
	if err != nil || winner.URL != "https://late.example/1" {
		t.Fatalf("winner %s, err %v", winner.URL, err)
	}
}

func TestPickBestPrefersALocalCopy(t *testing.T) {
	withRaceTiming(t, 50*time.Millisecond)
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://fast.example/1":   {delay: 1 * time.Millisecond, bytesPerSecond: 1e9},
		"https://cached.example/1": {delay: 5 * time.Millisecond, cached: true},
	})
	winner, _ := pickBest(context.Background(), []string{"https://fast.example/1", "https://cached.example/1"}, trier, ignoreOutcome)
	if winner.URL != "https://cached.example/1" {
		t.Fatalf("winner %s", winner.URL)
	}
}

func TestPickBestMovesOnToTheNextRoundWhenNothingPlays(t *testing.T) {
	withRaceTiming(t, 20*time.Millisecond)
	trier, tried := fakeTrier(map[string]fakeLink{
		"https://same.example/1":  {delay: 1 * time.Millisecond, fails: true},
		"https://same.example/2":  {delay: 1 * time.Millisecond, bytesPerSecond: 10},
		"https://other.example/1": {delay: 1 * time.Millisecond, fails: true},
	})
	var recorded []Outcome
	var mutex sync.Mutex
	record := func(outcome Outcome) {
		mutex.Lock()
		recorded = append(recorded, outcome)
		mutex.Unlock()
	}
	winner, err := pickBest(context.Background(), []string{"https://same.example/1", "https://same.example/2", "https://other.example/1"}, trier, record)
	if err != nil || winner.URL != "https://same.example/2" {
		t.Fatalf("winner %s, err %v", winner.URL, err)
	}
	// One link per host per round: same.example/2 only ran in round two.
	if links := tried(); len(links) != 3 || links[2] != "https://same.example/2" {
		t.Fatalf("tried %v", links)
	}
	if len(recorded) != 3 {
		t.Fatalf("recorded %d outcomes, want 3", len(recorded))
	}
}

func TestPickBestReportsWhenNothingPlays(t *testing.T) {
	withRaceTiming(t, 10*time.Millisecond)
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://a.example/1": {delay: 1 * time.Millisecond, fails: true},
		"https://b.example/1": {delay: 1 * time.Millisecond, fails: true},
	})
	_, err := pickBest(context.Background(), []string{"https://a.example/1", "https://b.example/1"}, trier, ignoreOutcome)
	if !errors.Is(err, ErrNothingPlays) {
		t.Fatalf("err = %v", err)
	}
}

func TestNextRoundCapsTheRoundAndKeepsTheOrder(t *testing.T) {
	links := []string{
		"https://voe.sx/1", "https://voe.sx/2", "https://dood.to/1", "https://a.example/1",
		"https://b.example/1", "https://c.example/1", "https://d.example/1",
	}
	round, rest := nextRound(links)
	want := []string{"https://voe.sx/1", "https://dood.to/1", "https://a.example/1", "https://b.example/1", "https://c.example/1"}
	if len(round) != len(want) {
		t.Fatalf("round %v", round)
	}
	for index := range want {
		if round[index] != want[index] {
			t.Fatalf("round %v", round)
		}
	}
	if len(rest) != 2 || rest[0] != "https://voe.sx/2" || rest[1] != "https://d.example/1" {
		t.Fatalf("rest %v", rest)
	}
}
