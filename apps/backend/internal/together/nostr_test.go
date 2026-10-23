package together

import (
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
)

func TestEventSignatureVerifies(t *testing.T) {
	key, err := btcec.NewPrivateKey()
	if err != nil {
		t.Fatal(err)
	}
	event := nostrEvent{CreatedAt: 1700000000, Kind: signalKind, Tags: [][]string{{"t", "abc"}}, Content: "a+b/c="}
	if err := event.sign(key); err != nil {
		t.Fatal(err)
	}
	publicKeyBytes, _ := hex.DecodeString(event.PubKey)
	publicKey, err := schnorr.ParsePubKey(publicKeyBytes)
	if err != nil {
		t.Fatal(err)
	}
	signatureBytes, _ := hex.DecodeString(event.Sig)
	signature, err := schnorr.ParseSignature(signatureBytes)
	if err != nil {
		t.Fatal(err)
	}
	hash, _ := hex.DecodeString(event.ID)
	if !signature.Verify(hash, publicKey) {
		t.Fatal("signature does not verify")
	}
}

func TestParseEventFrameReadsEventsOnly(t *testing.T) {
	frame := []byte(`["EVENT","sub",{"id":"1","kind":25050,"tags":[["t","abc"]],"content":"x"}]`)
	event, ok := parseEventFrame(frame)
	if !ok || event.Content != "x" || !event.hasTopic("abc") || event.hasTopic("other") {
		t.Fatalf("parseEventFrame = %+v, %v", event, ok)
	}
	if _, ok := parseEventFrame([]byte(`["EOSE","sub"]`)); ok {
		t.Fatal("EOSE parsed as event")
	}
}

func TestDeliverDropsDuplicates(t *testing.T) {
	channel := &nostrChannel{topic: "abc", received: make(chan string, 4), seen: map[string]bool{}}
	event := nostrEvent{ID: "1", Tags: [][]string{{"t", "abc"}}, Content: "x"}
	channel.deliver(event)
	channel.deliver(event)
	channel.deliver(nostrEvent{ID: "2", Tags: [][]string{{"t", "other"}}, Content: "y"})
	if len(channel.received) != 1 {
		t.Fatalf("delivered %d messages, want 1", len(channel.received))
	}
}
