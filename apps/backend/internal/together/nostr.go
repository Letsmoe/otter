package together

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"sync"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/gorilla/websocket"
)

// Public relays used until neoworks hosts its own (neoworks-dev/otter#1).
var DefaultRelays = []string{
	"wss://relay.damus.io",
	"wss://nos.lol",
	"wss://relay.primal.net",
}

// Ephemeral range (20000–29999): relays forward these and never store them.
const signalKind = 25050

const (
	relayRetryDelay   = 3 * time.Second
	relayDialTimeout  = 10 * time.Second
	relayWriteTimeout = 5 * time.Second
	// Received event IDs remembered for deduplication across relays.
	seenLimit = 512
)

var errNoRelay = errors.New("no signaling relay reachable")

// Signaling carries sealed handshake messages between peers of one room.
type Signaling interface {
	Open(ctx context.Context, topic string) (SignalChannel, error)
}

// SignalChannel is one room's topic: everything sent reaches every other
// participant subscribed to the same topic.
type SignalChannel interface {
	Send(ctx context.Context, content string) error
	Receive() <-chan string
}

// NostrSignaling publishes and subscribes on public Nostr relays.
type NostrSignaling struct {
	Relays []string
}

func (signaling NostrSignaling) Open(ctx context.Context, topic string) (SignalChannel, error) {
	key, err := btcec.NewPrivateKey()
	if err != nil {
		return nil, err
	}
	channel := &nostrChannel{
		topic:    topic,
		key:      key,
		received: make(chan string, 64),
		seen:     map[string]bool{},
	}
	for _, url := range signaling.Relays {
		relay := &relayConnection{url: url, channel: channel}
		channel.relays = append(channel.relays, relay)
		go relay.run(ctx)
	}
	return channel, nil
}

type nostrChannel struct {
	topic    string
	key      *btcec.PrivateKey
	relays   []*relayConnection
	received chan string

	seenMutex sync.Mutex
	seen      map[string]bool
	seenOrder []string
}

func (channel *nostrChannel) Receive() <-chan string {
	return channel.received
}

func (channel *nostrChannel) Send(ctx context.Context, content string) error {
	event := nostrEvent{
		CreatedAt: time.Now().Unix(),
		Kind:      signalKind,
		Tags:      [][]string{{"t", channel.topic}},
		Content:   content,
	}
	if err := event.sign(channel.key); err != nil {
		return err
	}
	frame, err := marshalNostr([]any{"EVENT", event})
	if err != nil {
		return err
	}
	sent := 0
	for _, relay := range channel.relays {
		if relay.write(frame) == nil {
			sent++
		}
	}
	if sent == 0 {
		return errNoRelay
	}
	return nil
}

// deliver passes on an event's content once, however many relays echo it.
func (channel *nostrChannel) deliver(event nostrEvent) {
	if !event.hasTopic(channel.topic) || channel.markSeen(event.ID) {
		return
	}
	select {
	case channel.received <- event.Content:
	default:
		log.Printf("together: signaling buffer full, dropped a message")
	}
}

// markSeen records an event ID and reports whether it was already there.
func (channel *nostrChannel) markSeen(eventID string) bool {
	channel.seenMutex.Lock()
	defer channel.seenMutex.Unlock()
	if channel.seen[eventID] {
		return true
	}
	channel.seen[eventID] = true
	channel.seenOrder = append(channel.seenOrder, eventID)
	if len(channel.seenOrder) > seenLimit {
		delete(channel.seen, channel.seenOrder[0])
		channel.seenOrder = channel.seenOrder[1:]
	}
	return false
}

func (channel *nostrChannel) subscribeFrame() ([]byte, error) {
	filter := map[string]any{
		"kinds": []int{signalKind},
		"#t":    []string{channel.topic},
		"since": time.Now().Add(-time.Minute).Unix(),
	}
	return marshalNostr([]any{"REQ", "otter-" + channel.topic[:8], filter})
}

// relayConnection keeps one relay subscribed until the room's context ends.
type relayConnection struct {
	url     string
	channel *nostrChannel

	mutex      sync.Mutex
	connection *websocket.Conn
}

func (relay *relayConnection) run(ctx context.Context) {
	for ctx.Err() == nil {
		if err := relay.session(ctx); err != nil && ctx.Err() == nil {
			log.Printf("together: relay %s: %v", relay.url, err)
		}
		select {
		case <-ctx.Done():
		case <-time.After(relayRetryDelay):
		}
	}
}

// session connects, subscribes and reads until the connection drops.
func (relay *relayConnection) session(ctx context.Context) error {
	dialContext, cancel := context.WithTimeout(ctx, relayDialTimeout)
	defer cancel()
	connection, _, err := websocket.DefaultDialer.DialContext(dialContext, relay.url, nil)
	if err != nil {
		return err
	}
	relay.setConnection(connection)
	defer relay.setConnection(nil)
	// Unblocks ReadMessage when the room closes.
	stop := context.AfterFunc(ctx, func() { connection.Close() })
	defer stop()

	subscribe, err := relay.channel.subscribeFrame()
	if err != nil {
		return err
	}
	if err := relay.write(subscribe); err != nil {
		return err
	}
	return relay.readLoop(connection)
}

func (relay *relayConnection) readLoop(connection *websocket.Conn) error {
	for {
		_, frame, err := connection.ReadMessage()
		if err != nil {
			return err
		}
		event, ok := parseEventFrame(frame)
		if ok {
			relay.channel.deliver(event)
		}
	}
}

func (relay *relayConnection) setConnection(connection *websocket.Conn) {
	relay.mutex.Lock()
	defer relay.mutex.Unlock()
	if connection == nil && relay.connection != nil {
		relay.connection.Close()
	}
	relay.connection = connection
}

func (relay *relayConnection) write(frame []byte) error {
	relay.mutex.Lock()
	defer relay.mutex.Unlock()
	if relay.connection == nil {
		return errNoRelay
	}
	relay.connection.SetWriteDeadline(time.Now().Add(relayWriteTimeout))
	return relay.connection.WriteMessage(websocket.TextMessage, frame)
}

// parseEventFrame reads ["EVENT", subscriptionId, event]; other frames
// (EOSE, OK, NOTICE) are ignored.
func parseEventFrame(frame []byte) (nostrEvent, bool) {
	var parts []json.RawMessage
	if json.Unmarshal(frame, &parts) != nil || len(parts) < 3 {
		return nostrEvent{}, false
	}
	var label string
	if json.Unmarshal(parts[0], &label) != nil || label != "EVENT" {
		return nostrEvent{}, false
	}
	var event nostrEvent
	if json.Unmarshal(parts[2], &event) != nil {
		return nostrEvent{}, false
	}
	return event, true
}

// nostrEvent follows NIP-01. Signatures of received events are not checked:
// the AES-GCM seal already proves a message comes from someone with the code.
type nostrEvent struct {
	ID        string     `json:"id"`
	PubKey    string     `json:"pubkey"`
	CreatedAt int64      `json:"created_at"`
	Kind      int        `json:"kind"`
	Tags      [][]string `json:"tags"`
	Content   string     `json:"content"`
	Sig       string     `json:"sig"`
}

func (event *nostrEvent) sign(key *btcec.PrivateKey) error {
	event.PubKey = hex.EncodeToString(schnorr.SerializePubKey(key.PubKey()))
	serialized, err := marshalNostr([]any{0, event.PubKey, event.CreatedAt, event.Kind, event.Tags, event.Content})
	if err != nil {
		return err
	}
	hash := sha256.Sum256(serialized)
	signature, err := schnorr.Sign(key, hash[:])
	if err != nil {
		return err
	}
	event.ID = hex.EncodeToString(hash[:])
	event.Sig = hex.EncodeToString(signature.Serialize())
	return nil
}

func (event nostrEvent) hasTopic(topic string) bool {
	for _, tag := range event.Tags {
		if len(tag) >= 2 && tag[0] == "t" && tag[1] == topic {
			return true
		}
	}
	return false
}

// marshalNostr encodes without HTML escaping, as NIP-01 hashing requires.
func marshalNostr(value any) ([]byte, error) {
	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return bytes.TrimRight(buffer.Bytes(), "\n"), nil
}
