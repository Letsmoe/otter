package together

import (
	"context"
	"os"
	"testing"
)

// Talks to the public relays and STUN servers; run with OTTER_LIVE_TOGETHER=1.
func TestLiveRelaysConnect(t *testing.T) {
	if os.Getenv("OTTER_LIVE_TOGETHER") == "" {
		t.Skip("set OTTER_LIVE_TOGETHER=1 to use the public relays")
	}
	signaling := NostrSignaling{Relays: DefaultRelays}
	host := NewService(signaling, DefaultICEServers)
	guest := NewService(signaling, DefaultICEServers)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	hostUpdates := host.Subscribe(ctx)
	guestUpdates := guest.Subscribe(ctx)

	room, err := host.Host(Peer{Name: "Ana"})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := guest.Join(Peer{Name: "Ben"}, room.Code); err != nil {
		t.Fatal(err)
	}
	waitFor(t, hostUpdates, connected)
	waitFor(t, guestUpdates, connected)
	host.Leave()
	guest.Leave()
}
