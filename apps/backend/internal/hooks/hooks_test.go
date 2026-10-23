package hooks

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestEmitRunsExecutableScriptsWithEventAndPayload(t *testing.T) {
	directory := t.TempDir()
	output := filepath.Join(directory, "out.txt")
	script := "#!/bin/sh\necho \"$OTTER_EVENT $(cat)\" > " + output + "\n"
	if err := os.WriteFile(filepath.Join(directory, "record.sh"), []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "notes.txt"), []byte("not a hook"), 0o644); err != nil {
		t.Fatal(err)
	}

	runner := New(directory, nil)
	if got := runner.scripts(); len(got) != 1 {
		t.Fatalf("scripts = %v, want only record.sh", got)
	}
	runner.Emit(EventWatchCompleted, map[string]string{"title": "Heat"})

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		written, err := os.ReadFile(output)
		if err == nil && strings.TrimSpace(string(written)) == `watch.completed {"title":"Heat"}` {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("hook did not record the event")
}
