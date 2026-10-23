package together

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

// memorySignaling is an in-process relay: every send reaches every
// subscriber of the topic, the sender included.
type memorySignaling struct {
	mutex       sync.Mutex
	subscribers map[string][]chan string
}

type memoryChannel struct {
	bus      *memorySignaling
	topic    string
	received chan string
}

func (bus *memorySignaling) Open(ctx context.Context, topic string) (SignalChannel, error) {
	channel := &memoryChannel{bus: bus, topic: topic, received: make(chan string, 64)}
	bus.mutex.Lock()
	bus.subscribers[topic] = append(bus.subscribers[topic], channel.received)
	bus.mutex.Unlock()
	return channel, nil
}

func (channel *memoryChannel) Receive() <-chan string {
	return channel.received
}

func (channel *memoryChannel) Send(ctx context.Context, content string) error {
	channel.bus.mutex.Lock()
	defer channel.bus.mutex.Unlock()
	for _, subscriber := range channel.bus.subscribers[channel.topic] {
		subscriber <- content
	}
	return nil
}

func loopbackService(bus *memorySignaling) *Service {
	settings := webrtc.SettingEngine{}
	settings.SetIncludeLoopbackCandidate(true)
	settings.SetNetworkTypes([]webrtc.NetworkType{webrtc.NetworkTypeUDP4})
	return newService(bus, nil, settings)
}

func waitFor(t *testing.T, updates <-chan Update, matches func(Update) bool) Update {
	t.Helper()
	timeout := time.After(20 * time.Second)
	for {
		select {
		case update := <-updates:
			if matches(update) {
				return update
			}
		case <-timeout:
			t.Fatal("timed out waiting for update")
		}
	}
}

func connected(update Update) bool {
	return update.Room != nil && update.Room.Status == StatusConnected && len(update.Room.Peers) == 2
}

func TestHostAndGuestConnectAndExchangeMessages(t *testing.T) {
	bus := &memorySignaling{subscribers: map[string][]chan string{}}
	host := loopbackService(bus)
	guest := loopbackService(bus)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	hostUpdates := host.Subscribe(ctx)
	guestUpdates := guest.Subscribe(ctx)

	room, err := host.Host(Peer{Name: "Ana", Avatar: "otter:classic"})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := guest.Join(Peer{Name: "Ben"}, room.Code); err != nil {
		t.Fatal(err)
	}
	waitFor(t, hostUpdates, connected)
	waitFor(t, guestUpdates, connected)

	if err := guest.Send(`{"type":"chat"}`); err != nil {
		t.Fatal(err)
	}
	received := waitFor(t, hostUpdates, func(update Update) bool { return update.Message != nil })
	if received.Message.From.Name != "Ben" || received.Message.Payload != `{"type":"chat"}` {
		t.Fatalf("host received %+v", received.Message)
	}

	guest.Leave()
	waitFor(t, hostUpdates, func(update Update) bool {
		return update.Room != nil && update.Room.Status == StatusWaiting && len(update.Room.Peers) == 1
	})
	host.Leave()
}
