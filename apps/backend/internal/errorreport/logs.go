package errorreport

import (
	"io"
	"log"
	"strings"
	"sync"
)

// The backend's last log lines go with every report: what it did right before.
const recentLogLines = 200

// recentLog keeps the last log lines; the log package writes one line per Write.
type recentLog struct {
	mutex sync.Mutex
	lines []string
}

var recentLogs = &recentLog{}

// keepRecentLogs copies the standard logger's output into recentLogs.
func keepRecentLogs() {
	log.SetOutput(io.MultiWriter(log.Writer(), recentLogs))
}

func (recent *recentLog) Write(data []byte) (int, error) {
	recent.mutex.Lock()
	defer recent.mutex.Unlock()
	recent.lines = append(recent.lines, strings.TrimRight(string(data), "\n"))
	if len(recent.lines) > recentLogLines {
		recent.lines = recent.lines[len(recent.lines)-recentLogLines:]
	}
	return len(data), nil
}

func (recent *recentLog) text() string {
	recent.mutex.Lock()
	defer recent.mutex.Unlock()
	return strings.Join(recent.lines, "\n")
}
