// Package together syncs playback between otter instances ("Watch Together").
//
// Peers meet on Nostr relays: every handshake message is AES-GCM sealed with a
// key derived from the room code and tagged with a topic derived from that
// key. After the WebRTC handshake, everything runs peer-to-peer over a data
// channel. The host is the hub; guests' messages reach each other through it.
// Payloads are opaque here; the app defines what it sends.
package together

import (
	"context"
	"log"
	"sync"

	"github.com/google/uuid"
	"github.com/pion/webrtc/v4"
)

// Public STUN servers for NAT traversal; peers behind symmetric NAT would
// need TURN, which no public service offers for free.
var DefaultICEServers = []string{
	"stun:stun.l.google.com:19302",
	"stun:stun.cloudflare.com:3478",
}

// Update is pushed to subscribers whenever the room changes or a message
// arrives. Room is nil when there is no room.
type Update struct {
	Room    *Snapshot
	Message *Message
}

// Service holds the instance's one room; a phone runs one party at a time.
type Service struct {
	signaling Signaling
	api       *webrtc.API
	config    webrtc.Configuration

	mutex       sync.Mutex
	room        *room
	subscribers map[chan Update]struct{}
}

func NewService(signaling Signaling, iceServers []string) *Service {
	return newService(signaling, iceServers, webrtc.SettingEngine{})
}

func newService(signaling Signaling, iceServers []string, settings webrtc.SettingEngine) *Service {
	config := webrtc.Configuration{}
	if len(iceServers) > 0 {
		config.ICEServers = []webrtc.ICEServer{{URLs: iceServers}}
	}
	return &Service{
		signaling:   signaling,
		api:         webrtc.NewAPI(webrtc.WithSettingEngine(settings)),
		config:      config,
		subscribers: map[chan Update]struct{}{},
	}
}

// Host opens a new room and waits for guests.
func (service *Service) Host(self Peer) (*Snapshot, error) {
	code, err := NewCode()
	if err != nil {
		return nil, err
	}
	return service.open(code, self, true)
}

// Join enters the room with the given code and looks for its host.
func (service *Service) Join(self Peer, code string) (*Snapshot, error) {
	normalized, err := NormalizeCode(code)
	if err != nil {
		return nil, err
	}
	return service.open(normalized, self, false)
}

func (service *Service) open(code string, self Peer, hosting bool) (*Snapshot, error) {
	key, err := deriveRoomKey(code)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(context.Background())
	signals, err := service.signaling.Open(ctx, key.topic)
	if err != nil {
		cancel()
		return nil, err
	}
	self.ID = uuid.NewString()
	opened := &room{
		code:    code,
		hosting: hosting,
		self:    self,
		key:     key,
		signals: signals,
		api:     service.api,
		config:  service.config,
		ctx:     ctx,
		cancel:  cancel,
		status:  initialStatus(hosting),
		links:   map[string]*link{},
		roster:  []Peer{self},
	}
	opened.onChange = func() { service.roomChanged(opened) }
	opened.onMessage = func(message Message) { service.roomMessage(opened, message) }
	service.replaceRoom(opened)
	opened.start()
	snapshot := opened.snapshot()
	return &snapshot, nil
}

func initialStatus(hosting bool) Status {
	if hosting {
		return StatusWaiting
	}
	return StatusConnecting
}

// Leave closes the current room, if any.
func (service *Service) Leave() {
	service.replaceRoom(nil)
}

func (service *Service) replaceRoom(next *room) {
	service.mutex.Lock()
	previous := service.room
	service.room = next
	service.mutex.Unlock()
	if previous != nil {
		go previous.close()
	}
	service.notify(service.updateFor(next, nil))
}

// Send passes an app payload to everyone else in the room.
func (service *Service) Send(payload string) error {
	current := service.currentRoom()
	if current == nil {
		return errNotConnected
	}
	return current.Send(payload)
}

// Current is the room's state, or nil without one.
func (service *Service) Current() *Snapshot {
	current := service.currentRoom()
	if current == nil {
		return nil
	}
	snapshot := current.snapshot()
	return &snapshot
}

func (service *Service) currentRoom() *room {
	service.mutex.Lock()
	defer service.mutex.Unlock()
	return service.room
}

// Subscribe sends the current state first, then every update until ctx ends.
func (service *Service) Subscribe(ctx context.Context) <-chan Update {
	updates := make(chan Update, 64)
	updates <- Update{Room: service.Current()}
	service.mutex.Lock()
	service.subscribers[updates] = struct{}{}
	service.mutex.Unlock()
	go func() {
		<-ctx.Done()
		service.mutex.Lock()
		delete(service.subscribers, updates)
		service.mutex.Unlock()
		close(updates)
	}()
	return updates
}

// Callbacks from a room that was replaced in the meantime are dropped.
func (service *Service) roomChanged(changed *room) {
	if service.currentRoom() != changed {
		return
	}
	service.notify(service.updateFor(changed, nil))
}

func (service *Service) roomMessage(changed *room, message Message) {
	if service.currentRoom() != changed {
		return
	}
	service.notify(service.updateFor(changed, &message))
}

func (service *Service) updateFor(current *room, message *Message) Update {
	if current == nil {
		return Update{Message: message}
	}
	snapshot := current.snapshot()
	return Update{Room: &snapshot, Message: message}
}

func (service *Service) notify(update Update) {
	service.mutex.Lock()
	defer service.mutex.Unlock()
	for subscriber := range service.subscribers {
		select {
		case subscriber <- update:
		default:
			log.Printf("together: subscriber too slow, dropped an update")
		}
	}
}
