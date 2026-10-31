package resolve

import (
	"context"
	"slices"
	"sync"
	"testing"
	"time"
)

func TestCheckLinksRecordsEveryLink(t *testing.T) {
	trier, _ := fakeTrier(map[string]fakeLink{
		"https://a.example/1": {delay: time.Millisecond},
		"https://a.example/2": {delay: time.Millisecond, fails: true},
		"https://b.example/1": {delay: time.Millisecond},
	})
	var mutex sync.Mutex
	verdicts := map[string]bool{}
	checkLinks(context.Background(), []string{"https://a.example/1", "https://a.example/2", "https://b.example/1"}, trier, func(outcome Outcome) {
		mutex.Lock()
		defer mutex.Unlock()
		verdicts[outcome.URL] = outcome.plays()
	})
	expected := map[string]bool{"https://a.example/1": true, "https://a.example/2": false, "https://b.example/1": true}
	for link, plays := range expected {
		recorded, found := verdicts[link]
		if !found || recorded != plays {
			t.Fatalf("verdict of %s = %v (recorded: %v), want %v", link, recorded, found, plays)
		}
	}
}

func TestCheckLinksTriesOneHostInOrder(t *testing.T) {
	trier, triedLinks := fakeTrier(map[string]fakeLink{
		"https://a.example/1": {delay: 20 * time.Millisecond},
		"https://a.example/2": {delay: time.Millisecond},
		"https://a.example/3": {delay: time.Millisecond},
	})
	checkLinks(context.Background(), []string{"https://a.example/1", "https://a.example/2", "https://a.example/3"}, trier, ignoreOutcome)
	want := []string{"https://a.example/1", "https://a.example/2", "https://a.example/3"}
	if tried := triedLinks(); !slices.Equal(tried, want) {
		t.Fatalf("tried %v, want %v", tried, want)
	}
}
