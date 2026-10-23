package recommend

import (
	"slices"

	"github.com/neoworks/otter/internal/library"
)

// rerankState tracks the page being filled: per-person counters and the
// runs of items from the same source and of the same media type.
type rerankState struct {
	slot           int
	personCounts   map[int]int
	lastSource     string
	sameSourceRun  int
	lastMediaType  library.MediaType
	sameMediaTypes int
}

type constraint func(item scoredItem) bool

func newRerankState() *rerankState {
	return &rerankState{personCounts: map[int]int{}}
}

// rerank turns the score-ordered list into the feed order: every seventh
// slot goes to exploration, no page holds more than a couple of titles of
// one actor or director, and sources and media types don't run on for
// long. Items that don't fit a page are deferred, never dropped.
func rerank(scored []scoredItem) []Item {
	main, exploration := splitExploration(scored)
	state := newRerankState()
	ranked := make([]Item, 0, len(scored))
	for len(main)+len(exploration) > 0 {
		var picked scoredItem
		if len(main) == 0 || (state.isExplorationSlot() && len(exploration) > 0) {
			picked, exploration = takeAt(exploration, state.pickIndex(exploration))
		} else {
			picked, main = takeAt(main, state.pickIndex(main))
		}
		state.record(picked)
		ranked = append(ranked, picked.Item)
	}
	return ranked
}

func splitExploration(scored []scoredItem) (main, exploration []scoredItem) {
	for _, item := range scored {
		if isExploration(item.Source) {
			exploration = append(exploration, item)
			continue
		}
		main = append(main, item)
	}
	return main, exploration
}

func takeAt(queue []scoredItem, index int) (scoredItem, []scoredItem) {
	item := queue[index]
	return item, slices.Delete(queue, index, index+1)
}

func (state *rerankState) isExplorationSlot() bool {
	return (state.slot+1)%explorationEvery == 0
}

// pickIndex returns the best-scored item that keeps every constraint,
// dropping constraints weakest first when none does.
func (state *rerankState) pickIndex(queue []scoredItem) int {
	tiers := [][]constraint{
		{state.withinPersonCaps, state.continuesSourceRun, state.continuesMediaTypeRun},
		{state.withinPersonCaps, state.continuesSourceRun},
		{state.withinPersonCaps},
	}
	for _, constraints := range tiers {
		if index := firstSatisfying(queue, constraints); index >= 0 {
			return index
		}
	}
	return 0
}

func firstSatisfying(queue []scoredItem, constraints []constraint) int {
	for index, item := range queue {
		if satisfiesAll(item, constraints) {
			return index
		}
	}
	return -1
}

func satisfiesAll(item scoredItem, constraints []constraint) bool {
	for _, keeps := range constraints {
		if !keeps(item) {
			return false
		}
	}
	return true
}

func (state *rerankState) withinPersonCaps(item scoredItem) bool {
	for _, person := range item.features.people() {
		if state.personCounts[person] >= maxPerPersonPerPage {
			return false
		}
	}
	return true
}

func (state *rerankState) continuesSourceRun(item scoredItem) bool {
	return item.Source != state.lastSource || state.sameSourceRun < maxSameSourceRun
}

func (state *rerankState) continuesMediaTypeRun(item scoredItem) bool {
	return item.Media.MediaType != state.lastMediaType || state.sameMediaTypes < maxSameMediaTypeRun
}

// record fills the slot with item and advances, starting a fresh page when
// this one is full.
func (state *rerankState) record(item scoredItem) {
	for _, person := range item.features.people() {
		state.personCounts[person]++
	}
	if item.Source == state.lastSource {
		state.sameSourceRun++
	} else {
		state.lastSource = item.Source
		state.sameSourceRun = 1
	}
	if item.Media.MediaType == state.lastMediaType {
		state.sameMediaTypes++
	} else {
		state.lastMediaType = item.Media.MediaType
		state.sameMediaTypes = 1
	}
	state.slot++
	if state.slot%pageSize == 0 {
		state.personCounts = map[int]int{}
	}
}
