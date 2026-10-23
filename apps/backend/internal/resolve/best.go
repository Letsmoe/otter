package resolve

import (
	"context"
	"errors"
	"time"

	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/stream"
)

// Picking a link to play: several links are resolved and probed at once, and
// only one that delivered real video can win. Among those, the fastest wins
// once raceDecisionDelay has passed; before that, a race ends early when every
// link has answered. Links on the same host never race each other (one
// site's tokens and rate limits), they wait for a later round.

var (
	raceSize          = 5
	raceDecisionDelay = 2500 * time.Millisecond
	candidateTimeout  = 20 * time.Second
)

// ErrNothingPlays means every link was tried and none delivered video.
var ErrNothingPlays = errors.New("none of these links plays")

// Prober checks that a resolved stream delivers video and measures its speed.
type Prober interface {
	Probe(ctx context.Context, result *plugins.ResolveResult) (stream.ProbeResult, error)
}

// Outcome is what trying one link showed: a result, or the reason it failed.
type Outcome struct {
	URL    string
	Result *plugins.ResolveResult
	// Cached links play from local storage and beat any download.
	Cached         bool
	BytesPerSecond float64
	// Quality the probe saw, e.g. "1080p"; "" when unknown.
	Quality string
	Err     error
}

func (o Outcome) plays() bool {
	return o.Err == nil && o.Result != nil
}

// fasterThan ranks playing links: local copies first, then by speed.
func (o Outcome) fasterThan(other Outcome) bool {
	if o.Cached != other.Cached {
		return o.Cached
	}
	return o.BytesPerSecond > other.BytesPerSecond
}

type linkTrier func(ctx context.Context, link string) Outcome

// ResolveBest returns the best playing link among urls (most preferred
// first), resolved but not yet cached. record sees every finished attempt.
func (s *Service) ResolveBest(ctx context.Context, urls []string, prober Prober, record func(Outcome)) (Outcome, error) {
	tryLink := func(ctx context.Context, link string) Outcome {
		return s.tryLink(ctx, link, prober)
	}
	return pickBest(ctx, urls, tryLink, record)
}

func (s *Service) tryLink(ctx context.Context, link string, prober Prober) Outcome {
	ctx, cancel := context.WithTimeout(ctx, candidateTimeout)
	defer cancel()
	result, cached, err := s.ResolveWithoutCaching(ctx, link)
	if err != nil {
		return Outcome{URL: link, Err: err}
	}
	if cached {
		return Outcome{URL: link, Result: result, Cached: true}
	}
	probe, err := prober.Probe(ctx, result)
	if err != nil {
		return Outcome{URL: link, Err: err}
	}
	return Outcome{URL: link, Result: result, BytesPerSecond: probe.BytesPerSecond, Quality: probe.Quality()}
}

func pickBest(ctx context.Context, urls []string, tryLink linkTrier, record func(Outcome)) (Outcome, error) {
	remaining := urls
	for len(remaining) > 0 {
		var round []string
		round, remaining = nextRound(remaining)
		winner, found := race(ctx, round, tryLink, record)
		if found {
			return winner, nil
		}
		if ctx.Err() != nil {
			return Outcome{}, ctx.Err()
		}
	}
	return Outcome{}, ErrNothingPlays
}

// nextRound takes up to raceSize links on different hosts, in order; the
// others keep their order for later rounds.
func nextRound(urls []string) (round []string, rest []string) {
	hosts := map[string]bool{}
	for _, link := range urls {
		host := hostOf(link)
		if len(round) < raceSize && !hosts[host] {
			hosts[host] = true
			round = append(round, link)
			continue
		}
		rest = append(rest, link)
	}
	return round, rest
}

// race tries a round's links at once; unfinished ones are cancelled once a
// winner is chosen.
func race(ctx context.Context, round []string, tryLink linkTrier, record func(Outcome)) (Outcome, bool) {
	raceCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	outcomes := make(chan Outcome, len(round))
	for _, link := range round {
		go func() { outcomes <- tryLink(raceCtx, link) }()
	}
	return collectWinner(ctx, outcomes, len(round), record)
}

func collectWinner(ctx context.Context, outcomes <-chan Outcome, total int, record func(Outcome)) (Outcome, bool) {
	decision := time.NewTimer(raceDecisionDelay)
	defer decision.Stop()
	var best Outcome
	found := false
	decided := false
	for received := 0; received < total; {
		select {
		case outcome := <-outcomes:
			received++
			record(outcome)
			if outcome.plays() && (!found || outcome.fasterThan(best)) {
				best, found = outcome, true
			}
		case <-decision.C:
			decided = true
		case <-ctx.Done():
			return best, found
		}
		if found && decided {
			return best, true
		}
	}
	return best, found
}
