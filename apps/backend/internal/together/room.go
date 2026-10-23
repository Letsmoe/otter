package together

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"slices"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
)

type Status string

const (
	// Host alone in the room, waiting for someone to join.
	StatusWaiting Status = "WAITING"
	// Guest looking for the host.
	StatusConnecting Status = "CONNECTING"
	StatusConnected  Status = "CONNECTED"
	// The host left; the guest's room is over.
	StatusClosed Status = "CLOSED"
	// The guest never reached the host.
	StatusFailed Status = "FAILED"
)

const (
	// A guest repeats its join until the host answers; the relays forward
	// ephemeral events only to whoever is subscribed at that moment.
	joinInterval = 2 * time.Second
	joinTimeout  = 60 * time.Second
	// Without a STUN answer, ICE gathering still ends after this.
	gatherTimeout = 8 * time.Second
	byeFlushDelay = 200 * time.Millisecond
)

var errNotConnected = errors.New("not connected to anyone")

// Snapshot is a room as the API shows it.
type Snapshot struct {
	Code    string
	Hosting bool
	Status  Status
	Self    Peer
	Peers   []Peer
}

// Message is an app payload from another participant.
type Message struct {
	From    Peer
	Payload string
}

// link is the WebRTC connection to one other participant: every guest for
// the host, the host for a guest.
type link struct {
	peer       Peer
	connection *webrtc.PeerConnection
	// Set once the data channel is open.
	channel *webrtc.DataChannel
	// Our offer or answer, sent again when the other side repeats itself.
	localSDP  string
	remoteSDP string
}

// room is a star: guests connect to the host, who forwards between them.
type room struct {
	code    string
	hosting bool
	self    Peer
	key     *roomKey
	signals SignalChannel
	api     *webrtc.API
	config  webrtc.Configuration
	ctx     context.Context
	cancel  context.CancelFunc

	onChange  func()
	onMessage func(Message)

	mutex  sync.Mutex
	status Status
	links  map[string]*link
	roster []Peer
}

func (room *room) start() {
	go room.listen()
	if !room.hosting {
		go room.announce()
	}
}

func (room *room) snapshot() Snapshot {
	room.mutex.Lock()
	defer room.mutex.Unlock()
	return Snapshot{
		Code:    room.code,
		Hosting: room.hosting,
		Status:  room.status,
		Self:    room.self,
		Peers:   slices.Clone(room.roster),
	}
}

func (room *room) close() {
	if room.forward(wireMessage{Type: wireBye}, "") > 0 {
		// Let the bye leave before the connections close.
		time.Sleep(byeFlushDelay)
	}
	room.mutex.Lock()
	connections := make([]*webrtc.PeerConnection, 0, len(room.links))
	for _, peerLink := range room.links {
		if peerLink.connection != nil {
			connections = append(connections, peerLink.connection)
		}
	}
	room.links = map[string]*link{}
	room.mutex.Unlock()
	for _, connection := range connections {
		connection.Close()
	}
	room.cancel()
}

// Signaling ----------------------------------------------------------------

func (room *room) listen() {
	for {
		select {
		case <-room.ctx.Done():
			return
		case content := <-room.signals.Receive():
			room.handleSignal(content)
		}
	}
}

func (room *room) handleSignal(content string) {
	plain, err := room.key.open(content)
	if err != nil {
		return
	}
	var message signal
	if json.Unmarshal(plain, &message) != nil || message.From.ID == room.self.ID {
		return
	}
	if message.To != "" && message.To != room.self.ID {
		return
	}
	room.dispatchSignal(message)
}

func (room *room) dispatchSignal(message signal) {
	if room.hosting && message.Type == signalJoin {
		room.handleJoin(message)
		return
	}
	if room.hosting && message.Type == signalAnswer {
		room.handleAnswer(message)
		return
	}
	if !room.hosting && message.Type == signalOffer {
		room.handleOffer(message)
	}
}

func (room *room) publish(message signal) {
	message.From = room.self
	plain, err := json.Marshal(message)
	if err != nil {
		return
	}
	content, err := room.key.seal(plain)
	if err != nil {
		return
	}
	if err := room.signals.Send(room.ctx, content); err != nil {
		log.Printf("together: signaling: %v", err)
	}
}

// announce repeats the guest's join until the host's offer arrives.
func (room *room) announce() {
	ticker := time.NewTicker(joinInterval)
	defer ticker.Stop()
	deadline := time.After(joinTimeout)
	for room.currentStatus() == StatusConnecting {
		room.publish(signal{Type: signalJoin})
		select {
		case <-room.ctx.Done():
			return
		case <-deadline:
			room.failIfStillConnecting()
			return
		case <-ticker.C:
		}
	}
}

func (room *room) currentStatus() Status {
	room.mutex.Lock()
	defer room.mutex.Unlock()
	return room.status
}

func (room *room) failIfStillConnecting() {
	room.mutex.Lock()
	failed := room.status == StatusConnecting
	if failed {
		room.status = StatusFailed
	}
	room.mutex.Unlock()
	if failed {
		room.onChange()
	}
}

// Host side -----------------------------------------------------------------

func (room *room) handleJoin(message signal) {
	room.mutex.Lock()
	existing := room.links[message.From.ID]
	if existing == nil {
		created := &link{peer: message.From}
		room.links[message.From.ID] = created
		room.mutex.Unlock()
		go room.offer(created)
		return
	}
	localSDP := existing.localSDP
	room.mutex.Unlock()
	// The guest missed our offer; send it again.
	if localSDP != "" {
		room.publish(signal{Type: signalOffer, To: existing.peer.ID, SDP: localSDP})
	}
}

func (room *room) offer(peerLink *link) {
	connection, err := room.api.NewPeerConnection(room.config)
	if err != nil {
		log.Printf("together: peer connection: %v", err)
		return
	}
	room.setConnection(peerLink, connection)
	dataChannel, err := connection.CreateDataChannel("together", nil)
	if err != nil {
		log.Printf("together: data channel: %v", err)
		return
	}
	room.attachChannel(peerLink, dataChannel)
	description, err := connection.CreateOffer(nil)
	if err != nil {
		log.Printf("together: offer: %v", err)
		return
	}
	localSDP, err := room.setLocalAndGather(connection, description)
	if err != nil {
		log.Printf("together: offer: %v", err)
		return
	}
	room.setLocalSDP(peerLink, localSDP)
	room.publish(signal{Type: signalOffer, To: peerLink.peer.ID, SDP: localSDP})
}

func (room *room) handleAnswer(message signal) {
	room.mutex.Lock()
	peerLink := room.links[message.From.ID]
	accepted := peerLink != nil && peerLink.connection != nil && peerLink.remoteSDP == ""
	if accepted {
		peerLink.remoteSDP = message.SDP
	}
	room.mutex.Unlock()
	if !accepted {
		return
	}
	answer := webrtc.SessionDescription{Type: webrtc.SDPTypeAnswer, SDP: message.SDP}
	if err := peerLink.connection.SetRemoteDescription(answer); err != nil {
		log.Printf("together: answer: %v", err)
	}
}

// Guest side ----------------------------------------------------------------

func (room *room) handleOffer(message signal) {
	room.mutex.Lock()
	existing := room.links[message.From.ID]
	if existing != nil {
		repeat := existing.remoteSDP == message.SDP && existing.localSDP != ""
		localSDP := existing.localSDP
		room.mutex.Unlock()
		// Same offer again: our answer was likely lost.
		if repeat {
			room.publish(signal{Type: signalAnswer, To: existing.peer.ID, SDP: localSDP})
		}
		return
	}
	// A guest connects to one host only.
	if len(room.links) > 0 {
		room.mutex.Unlock()
		return
	}
	created := &link{peer: message.From, remoteSDP: message.SDP}
	room.links[message.From.ID] = created
	room.mutex.Unlock()
	go room.answer(created)
}

func (room *room) answer(peerLink *link) {
	connection, err := room.api.NewPeerConnection(room.config)
	if err != nil {
		log.Printf("together: peer connection: %v", err)
		return
	}
	room.setConnection(peerLink, connection)
	connection.OnDataChannel(func(dataChannel *webrtc.DataChannel) {
		room.attachChannel(peerLink, dataChannel)
	})
	offer := webrtc.SessionDescription{Type: webrtc.SDPTypeOffer, SDP: peerLink.remoteSDP}
	if err := connection.SetRemoteDescription(offer); err != nil {
		log.Printf("together: offer: %v", err)
		return
	}
	description, err := connection.CreateAnswer(nil)
	if err != nil {
		log.Printf("together: answer: %v", err)
		return
	}
	localSDP, err := room.setLocalAndGather(connection, description)
	if err != nil {
		log.Printf("together: answer: %v", err)
		return
	}
	room.setLocalSDP(peerLink, localSDP)
	room.publish(signal{Type: signalAnswer, To: peerLink.peer.ID, SDP: localSDP})
}

// WebRTC ---------------------------------------------------------------------

func (room *room) setConnection(peerLink *link, connection *webrtc.PeerConnection) {
	room.mutex.Lock()
	peerLink.connection = connection
	room.mutex.Unlock()
	room.watchConnection(peerLink)
}

func (room *room) setLocalSDP(peerLink *link, localSDP string) {
	room.mutex.Lock()
	peerLink.localSDP = localSDP
	room.mutex.Unlock()
}

// setLocalAndGather waits for every ICE candidate so the description can be
// sent in one signaling message (no trickle ICE).
func (room *room) setLocalAndGather(connection *webrtc.PeerConnection, description webrtc.SessionDescription) (string, error) {
	gathered := webrtc.GatheringCompletePromise(connection)
	if err := connection.SetLocalDescription(description); err != nil {
		return "", err
	}
	select {
	case <-gathered:
	case <-time.After(gatherTimeout):
	case <-room.ctx.Done():
		return "", room.ctx.Err()
	}
	return connection.LocalDescription().SDP, nil
}

func (room *room) watchConnection(peerLink *link) {
	peerLink.connection.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		if state == webrtc.PeerConnectionStateFailed || state == webrtc.PeerConnectionStateClosed {
			room.dropLink(peerLink)
		}
	})
}

func (room *room) attachChannel(peerLink *link, dataChannel *webrtc.DataChannel) {
	dataChannel.OnOpen(func() {
		room.linkOpened(peerLink, dataChannel)
	})
	dataChannel.OnMessage(func(message webrtc.DataChannelMessage) {
		room.receive(peerLink, message.Data)
	})
}

func (room *room) linkOpened(peerLink *link, dataChannel *webrtc.DataChannel) {
	room.mutex.Lock()
	peerLink.channel = dataChannel
	room.status = StatusConnected
	if room.hosting {
		room.roster = append(room.roster, peerLink.peer)
	}
	room.mutex.Unlock()
	if room.hosting {
		room.broadcastRoster()
	}
	room.onChange()
}

func (room *room) dropLink(peerLink *link) {
	room.mutex.Lock()
	if room.links[peerLink.peer.ID] != peerLink {
		room.mutex.Unlock()
		return
	}
	delete(room.links, peerLink.peer.ID)
	room.roster = slices.DeleteFunc(room.roster, func(peer Peer) bool { return peer.ID == peerLink.peer.ID })
	room.status = statusAfterDrop(room.hosting, len(room.links))
	connection := peerLink.connection
	room.mutex.Unlock()
	if connection != nil {
		connection.Close()
	}
	if room.hosting {
		room.broadcastRoster()
	}
	room.onChange()
}

func statusAfterDrop(hosting bool, remainingLinks int) Status {
	if !hosting {
		return StatusClosed
	}
	if remainingLinks == 0 {
		return StatusWaiting
	}
	return StatusConnected
}

// Data channel ---------------------------------------------------------------

func (room *room) receive(peerLink *link, data []byte) {
	var message wireMessage
	if json.Unmarshal(data, &message) != nil {
		return
	}
	switch message.Type {
	case wireBye:
		room.dropLink(peerLink)
	case wireRoster:
		room.receiveRoster(message.Peers)
	case wireApp:
		room.receiveApp(peerLink, message)
	}
}

func (room *room) receiveRoster(peers []Peer) {
	if room.hosting {
		return
	}
	room.mutex.Lock()
	room.roster = peers
	room.mutex.Unlock()
	room.onChange()
}

func (room *room) receiveApp(peerLink *link, message wireMessage) {
	// The host vouches for senders: a guest can't speak for someone else.
	sender := peerLink.peer
	if !room.hosting && message.From != nil {
		sender = *message.From
	}
	if room.hosting {
		room.forward(wireMessage{Type: wireApp, From: &sender, Payload: message.Payload}, sender.ID)
	}
	room.onMessage(Message{From: sender, Payload: message.Payload})
}

// Send passes an app payload to everyone else in the room.
func (room *room) Send(payload string) error {
	message := wireMessage{Type: wireApp, From: &room.self, Payload: payload}
	if room.forward(message, "") == 0 {
		return errNotConnected
	}
	return nil
}

func (room *room) broadcastRoster() {
	room.mutex.Lock()
	peers := slices.Clone(room.roster)
	room.mutex.Unlock()
	room.forward(wireMessage{Type: wireRoster, Peers: peers}, "")
}

// forward sends to every open link except the one with exceptPeerID and
// returns how many it reached.
func (room *room) forward(message wireMessage, exceptPeerID string) int {
	data, err := json.Marshal(message)
	if err != nil {
		return 0
	}
	reached := 0
	for _, dataChannel := range room.openChannels(exceptPeerID) {
		if dataChannel.SendText(string(data)) == nil {
			reached++
		}
	}
	return reached
}

func (room *room) openChannels(exceptPeerID string) []*webrtc.DataChannel {
	room.mutex.Lock()
	defer room.mutex.Unlock()
	channels := make([]*webrtc.DataChannel, 0, len(room.links))
	for peerID, peerLink := range room.links {
		if peerID != exceptPeerID && peerLink.channel != nil {
			channels = append(channels, peerLink.channel)
		}
	}
	return channels
}
