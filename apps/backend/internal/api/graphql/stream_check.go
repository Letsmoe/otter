package graphql

import (
	"context"
	"log"
	"sync"

	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/resolve"
)

// streamChecks remembers which titles' links are being checked, so opening a
// title twice doesn't test its links twice.
type streamChecks struct {
	mutex   sync.Mutex
	running map[library.MediaRef]bool
}

func newStreamChecks() *streamChecks {
	return &streamChecks{running: map[library.MediaRef]bool{}}
}

func (c *streamChecks) start(ref library.MediaRef) bool {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	if c.running[ref] {
		return false
	}
	c.running[ref] = true
	return true
}

func (c *streamChecks) finish(ref library.MediaRef) {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	delete(c.running, ref)
}

// checkStreams resolves and probes the stored links of a movie or episode and
// announces every verdict, so source lists can tell working links from broken
// ones before anyone presses play. Untested links only, unless recheck: then
// links that failed before get another chance too.
func (r *Resolver) checkStreams(ref library.MediaRef, recheck bool) {
	if r.requireVpnIfConfigured() != nil {
		return
	}
	if !r.streamChecks.start(ref) {
		return
	}
	defer r.streamChecks.finish(ref)

	ctx := context.Background()
	stored, err := r.Library.Store.Streams(ctx, ref)
	if err != nil {
		log.Printf("checking streams for %s: %v", mediaID(ref), err)
		return
	}
	links := linksToCheck(stored, recheck)
	if len(links) == 0 {
		return
	}
	r.StreamResolver.CheckLinks(ctx, links, r.StreamProxy, func(outcome resolve.Outcome) {
		r.recordOutcome(outcome)
		r.Library.AnnounceStreams(ctx, ref)
	})
}

// linksToCheck lists each stored URL once: the untested ones, or all of them
// on a recheck.
func linksToCheck(stored []library.Stream, recheck bool) []string {
	var links []string
	seen := map[string]bool{}
	for _, link := range stored {
		if seen[link.URL] {
			continue
		}
		seen[link.URL] = true
		if recheck || link.Playable == nil {
			links = append(links, link.URL)
		}
	}
	return links
}
