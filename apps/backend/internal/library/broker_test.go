package library

import (
	"context"
	"testing"
)

func TestBrokerKeepsTheFinalUpdateOfASlowSubscriber(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	broker := NewBroker()
	ref := MediaRef{MediaType: MediaTypeEpisode, MediaID: 1}
	updates := broker.Subscribe(ctx, ref)

	for count := 0; count < 20; count++ {
		broker.Publish(ref, StreamUpdate{})
	}
	broker.Publish(ref, StreamUpdate{Done: true})

	var last StreamUpdate
	for pending := len(updates); pending > 0; pending-- {
		last = <-updates
	}
	if !last.Done {
		t.Fatal("the final update was dropped")
	}
}
