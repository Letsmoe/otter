package together

// Peer is one participant: their otter profile's name and avatar.
type Peer struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Avatar string `json:"avatar"`
}

// Handshake messages, sealed and sent over the signaling relays.
const (
	signalJoin   = "join"
	signalOffer  = "offer"
	signalAnswer = "answer"
)

type signal struct {
	Type string `json:"type"`
	From Peer   `json:"from"`
	// Receiving peer ID for offers and answers.
	To  string `json:"to,omitempty"`
	SDP string `json:"sdp,omitempty"`
}

// Messages on the WebRTC data channel.
const (
	// Host → guests: everyone in the room.
	wireRoster = "roster"
	// App payload; the host forwards it to the other guests.
	wireApp = "app"
	// Sent before leaving so others don't wait for the connection to time out.
	wireBye = "bye"
)

type wireMessage struct {
	Type    string `json:"type"`
	From    *Peer  `json:"from,omitempty"`
	Peers   []Peer `json:"peers,omitempty"`
	Payload string `json:"payload,omitempty"`
}
