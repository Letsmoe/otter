package plugins

import (
	"context"
	"testing"
	"time"
)

func TestSetTimeoutRunsCallbacksInOrder(t *testing.T) {
	plugin := writeBundle(t, `
const order = [];
setTimeout((label) => order.push(label), 30, "slow");
setTimeout((label) => order.push(label), 5, "fast");
const cancelled = setTimeout(() => order.push("cancelled"), 10);
clearTimeout(cancelled);
clearTimeout(9999);
new Promise((resolve) => setTimeout(resolve, 60)).then(() => {
  require("fs").writeSync(1, JSON.stringify({ result: order }) + "\n");
  process.exit(0);
});
`)
	raw, err := NewEmbeddedRunner().invoke(context.Background(), plugin, "test", nil)
	if err != nil {
		t.Fatal(err)
	}
	if string(raw) != `["fast","slow"]` {
		t.Fatalf("order %s", raw)
	}
}

func TestSessionEndsWhenOnlyAClearedTimerRemained(t *testing.T) {
	plugin := writeBundle(t, `
const timer = setTimeout(() => {}, 60000);
clearTimeout(timer);
require("fs").writeSync(1, JSON.stringify({ result: "done" }) + "\n");
`)
	started := time.Now()
	raw, err := NewEmbeddedRunner().invoke(context.Background(), plugin, "test", nil)
	if err != nil {
		t.Fatal(err)
	}
	if string(raw) != `"done"` || time.Since(started) > 5*time.Second {
		t.Fatalf("raw %s after %s", raw, time.Since(started))
	}
}

func TestSetTimeoutStopsWithTheContext(t *testing.T) {
	plugin := writeBundle(t, `setTimeout(() => {}, 60000);`)
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	if _, err := NewEmbeddedRunner().invoke(ctx, plugin, "test", nil); err == nil {
		t.Fatal("expected the pending timer to end with the context")
	}
}
