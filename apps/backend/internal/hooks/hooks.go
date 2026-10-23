// Package hooks is otter's "bash tool": every executable file in the hooks
// directory runs on every event, with the event name in $OTTER_EVENT and the
// event payload as JSON on stdin. Scripts pick the events they care about,
// like Radarr/Sonarr custom scripts:
//
//	#!/bin/sh
//	[ "$OTTER_EVENT" = "watch.completed" ] || exit 0
//	jq -r .title >> "$HOME/watched.txt"
//
// Hooks run in the background with a timeout; their output goes to the log.
//
// On Android, apps may not exec files from their data directory, so there
// every *.sh file runs through the system shell instead.
package hooks

import (
	"bytes"
	"context"
	"encoding/json"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"time"
)

// Event names. Payload shapes are documented next to each Emit call.
const (
	EventTitleAdded     = "title.added"
	EventStreamsFound   = "streams.found"
	EventWatchStarted   = "watch.started"
	EventWatchCompleted = "watch.completed"
	EventRatingSet      = "rating.set"
	EventWatchlistAdded = "watchlist.added"
)

const hookTimeout = 2 * time.Minute

type Runner struct {
	directory   string
	environment []string
}

// New returns a runner for the scripts in directory, creating it if needed.
// environment is appended to every hook's environment (e.g. OTTER_URL).
func New(directory string, environment []string) *Runner {
	if err := os.MkdirAll(directory, 0o755); err != nil {
		log.Printf("hooks: create %s: %v", directory, err)
	}
	return &Runner{directory: directory, environment: environment}
}

// Emit runs every hook script for an event in the background.
func (r *Runner) Emit(event string, payload any) {
	if r == nil {
		return
	}
	scripts := r.scripts()
	if len(scripts) == 0 {
		return
	}
	encoded, err := json.Marshal(payload)
	if err != nil {
		log.Printf("hooks: encode %s payload: %v", event, err)
		return
	}
	for _, script := range scripts {
		go r.run(script, event, encoded)
	}
}

// scripts lists the executable regular files in the hooks directory, sorted.
func (r *Runner) scripts() []string {
	entries, err := os.ReadDir(r.directory)
	if err != nil {
		return nil
	}
	scripts := []string{}
	for _, entry := range entries {
		if isExecutableFile(entry) {
			scripts = append(scripts, filepath.Join(r.directory, entry.Name()))
		}
	}
	slices.Sort(scripts)
	return scripts
}

func isExecutableFile(entry os.DirEntry) bool {
	if !entry.Type().IsRegular() {
		return false
	}
	if runtime.GOOS == "android" {
		return strings.HasSuffix(entry.Name(), ".sh")
	}
	info, err := entry.Info()
	if err != nil {
		return false
	}
	return info.Mode().Perm()&0o111 != 0
}

func (r *Runner) run(script, event string, payload []byte) {
	ctx, cancel := context.WithTimeout(context.Background(), hookTimeout)
	defer cancel()

	command := hookCommand(ctx, script)
	command.Dir = r.directory
	command.Stdin = bytes.NewReader(payload)
	command.Env = append(os.Environ(), r.environment...)
	command.Env = append(command.Env, "OTTER_EVENT="+event)
	output, err := command.CombinedOutput()
	name := filepath.Base(script)
	if len(output) > 0 {
		log.Printf("hook %s (%s): %s", name, event, bytes.TrimSpace(output))
	}
	if err != nil {
		log.Printf("hook %s (%s) failed: %v", name, event, err)
	}
}

func hookCommand(ctx context.Context, script string) *exec.Cmd {
	if runtime.GOOS == "android" {
		return exec.CommandContext(ctx, "/system/bin/sh", script)
	}
	return exec.CommandContext(ctx, script)
}
