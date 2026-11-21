package library

import "sort"

// RemainingMinutes is how long a title still takes: its runtime minus what was
// watched, rounded up to whole minutes. 0 when the runtime is unknown.
func RemainingMinutes(runtimeMinutes int, watchedSeconds float64) int {
	if runtimeMinutes <= 0 {
		return 0
	}
	remaining := runtimeMinutes - int(watchedSeconds/60)
	if remaining < 1 {
		return 1
	}
	return remaining
}

// FitsMinutes reports whether something that takes [needed] minutes fits into [budget]. Unknown lengths (0) never fit.
func FitsMinutes(needed, budget int) bool {
	return needed > 0 && needed <= budget
}

// FilmsThatFit keeps the movies of titles whose runtime fits into budget minutes, the ones that
// use the time best first. Titles without a runtime are left out.
func FilmsThatFit(titles []Title, budget int) []Title {
	fitting := []Title{}
	for _, title := range titles {
		if title.MediaType == MediaTypeMovie && FitsMinutes(title.Runtime, budget) {
			fitting = append(fitting, title)
		}
	}
	sort.SliceStable(fitting, func(first, second int) bool { return fitting[first].Runtime > fitting[second].Runtime })
	return fitting
}
