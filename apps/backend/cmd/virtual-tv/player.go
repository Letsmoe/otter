package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/exec"
	"sync"
	"time"
)

// player shows what the TV plays in an mpv window, driven over mpv's JSON IPC socket.
type player struct {
	socketPath string
	mutex      sync.Mutex
	requestID  int
	uri        string
	stopped    bool
	volume     int
}

// mpvAnswer is one line mpv writes back on its IPC socket.
type mpvAnswer struct {
	RequestID int             `json:"request_id"`
	Error     string          `json:"error"`
	Data      json.RawMessage `json:"data"`
}

// startPlayer opens an empty mpv window that waits for streams.
func startPlayer(mpvPath string, title string) (*player, error) {
	socketPath := fmt.Sprintf("%s/virtual-tv-%d.sock", os.TempDir(), os.Getpid())
	command := exec.Command(mpvPath,
		"--idle=yes",
		"--force-window=yes",
		"--keep-open=no",
		"--no-terminal",
		"--osd-level=1",
		"--geometry=960x540",
		"--title="+title,
		"--input-ipc-server="+socketPath,
	)
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	if err := command.Start(); err != nil {
		return nil, fmt.Errorf("starting mpv: %w", err)
	}
	tv := &player{socketPath: socketPath, stopped: true, volume: 50}
	if err := tv.waitForSocket(); err != nil {
		return nil, err
	}
	return tv, nil
}

func (tv *player) waitForSocket() error {
	for attempt := 0; attempt < 50; attempt++ {
		connection, err := net.Dial("unix", tv.socketPath)
		if err == nil {
			connection.Close()
			return nil
		}
		time.Sleep(100 * time.Millisecond)
	}
	return fmt.Errorf("mpv didn't open %s", tv.socketPath)
}

// command sends one mpv command and returns its data.
func (tv *player) command(arguments ...any) (json.RawMessage, error) {
	tv.mutex.Lock()
	tv.requestID++
	requestID := tv.requestID
	tv.mutex.Unlock()
	connection, err := net.DialTimeout("unix", tv.socketPath, time.Second)
	if err != nil {
		return nil, err
	}
	defer connection.Close()
	connection.SetDeadline(time.Now().Add(2 * time.Second))
	request, _ := json.Marshal(map[string]any{"command": arguments, "request_id": requestID})
	if _, err := connection.Write(append(request, '\n')); err != nil {
		return nil, err
	}
	return readAnswer(connection, requestID)
}

// readAnswer skips mpv's event lines until the answer to requestID.
func readAnswer(connection net.Conn, requestID int) (json.RawMessage, error) {
	lines := bufio.NewScanner(connection)
	for lines.Scan() {
		var answer mpvAnswer
		if json.Unmarshal(lines.Bytes(), &answer) != nil || answer.RequestID != requestID {
			continue
		}
		if answer.Error != "success" {
			return nil, fmt.Errorf("mpv: %s", answer.Error)
		}
		return answer.Data, nil
	}
	return nil, fmt.Errorf("mpv closed the socket: %v", lines.Err())
}

func (tv *player) setURI(uri string) {
	tv.mutex.Lock()
	defer tv.mutex.Unlock()
	tv.uri = uri
}

func (tv *player) play() error {
	tv.mutex.Lock()
	uri := tv.uri
	wasStopped := tv.stopped
	tv.stopped = false
	tv.mutex.Unlock()
	if wasStopped {
		_, err := tv.command("loadfile", uri, "replace")
		return err
	}
	_, err := tv.command("set_property", "pause", false)
	return err
}

func (tv *player) pause() error {
	_, err := tv.command("set_property", "pause", true)
	return err
}

func (tv *player) stop() error {
	tv.mutex.Lock()
	tv.stopped = true
	tv.mutex.Unlock()
	_, err := tv.command("stop")
	return err
}

func (tv *player) seek(seconds float64) error {
	_, err := tv.command("seek", seconds, "absolute")
	return err
}

func (tv *player) setVolume(volume int) error {
	tv.mutex.Lock()
	tv.volume = volume
	tv.mutex.Unlock()
	_, err := tv.command("set_property", "volume", volume)
	return err
}

func (tv *player) currentVolume() int {
	tv.mutex.Lock()
	defer tv.mutex.Unlock()
	return tv.volume
}

func (tv *player) currentURI() string {
	tv.mutex.Lock()
	defer tv.mutex.Unlock()
	return tv.uri
}

// transportState answers GetTransportInfo the way a TV would.
func (tv *player) transportState() string {
	tv.mutex.Lock()
	uri := tv.uri
	stopped := tv.stopped
	tv.mutex.Unlock()
	if uri == "" {
		return "NO_MEDIA_PRESENT"
	}
	if stopped || tv.flag("idle-active") {
		return "STOPPED"
	}
	if tv.flag("pause") {
		return "PAUSED_PLAYBACK"
	}
	if tv.flag("paused-for-cache") || tv.flag("core-idle") {
		return "TRANSITIONING"
	}
	return "PLAYING"
}

func (tv *player) flag(property string) bool {
	data, err := tv.command("get_property", property)
	if err != nil {
		return false
	}
	var value bool
	json.Unmarshal(data, &value)
	return value
}

// seconds reads a time property; 0 when mpv has none (nothing loaded, a live stream's duration).
func (tv *player) seconds(property string) float64 {
	data, err := tv.command("get_property", property)
	if err != nil {
		return 0
	}
	var value float64
	json.Unmarshal(data, &value)
	return value
}
