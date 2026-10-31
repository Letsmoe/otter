package resolve

import (
	"context"
	"sync"
)

// CheckLinks resolves and probes every link to learn whether it plays,
// without starting playback or caching. Hosts are checked in parallel, the
// links of one host one after another (one site's tokens and rate limits).
// record sees every outcome as it arrives.
func (s *Service) CheckLinks(ctx context.Context, urls []string, prober Prober, record func(Outcome)) {
	tryLink := func(ctx context.Context, link string) Outcome {
		return s.tryLink(ctx, link, prober)
	}
	checkLinks(ctx, urls, tryLink, record)
}

func checkLinks(ctx context.Context, urls []string, tryLink linkTrier, record func(Outcome)) {
	var waitGroup sync.WaitGroup
	for _, hostLinks := range linksByHost(urls) {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			checkOneByOne(ctx, hostLinks, tryLink, record)
		}()
	}
	waitGroup.Wait()
}

func checkOneByOne(ctx context.Context, links []string, tryLink linkTrier, record func(Outcome)) {
	for _, link := range links {
		if ctx.Err() != nil {
			return
		}
		record(tryLink(ctx, link))
	}
}

// linksByHost groups links by host, keeping their order within a host.
func linksByHost(urls []string) [][]string {
	var groups [][]string
	indexByHost := map[string]int{}
	for _, link := range urls {
		host := hostOf(link)
		index, found := indexByHost[host]
		if !found {
			indexByHost[host] = len(groups)
			groups = append(groups, []string{link})
			continue
		}
		groups[index] = append(groups[index], link)
	}
	return groups
}
