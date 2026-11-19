package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/challenge"
)

// challengeUpdates turns the service's requests into subscription payloads until ctx ends.
func challengeUpdates(ctx context.Context, requests <-chan challenge.Request) <-chan *model.Challenge {
	updates := make(chan *model.Challenge)
	go func() {
		defer close(updates)
		for request := range requests {
			select {
			case updates <- challengeModel(request):
			case <-ctx.Done():
				return
			}
		}
	}()
	return updates
}

func challengeModel(request challenge.Request) *model.Challenge {
	kind := model.ChallengeKindClearance
	if request.Kind == challenge.KindNavigation {
		kind = model.ChallengeKindNavigation
	}
	return &model.Challenge{ID: request.ID, Kind: kind, URL: request.URL, Domain: request.Domain, Interactive: request.Interactive}
}
