// Package errorreport sends errors to PostHog error tracking (the Otter
// project), so failures on other people's installs can be investigated.
// Every function is a no-op until Start ran and while reporting is off.
package errorreport

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"net/http"
	"os"
	"runtime"
	"runtime/debug"
	"strings"
	"sync"
	"time"

	"github.com/posthog/posthog-go"
)

// ProjectToken is the public ingest key of the Otter PostHog project; builds
// can replace it with -ldflags "-X …/internal/errorreport.ProjectToken=…",
// an empty one turns reporting off.
var ProjectToken = "phc_kWtXWGtB5xAPwYhcu7uphFdBUgLTdhdK3g8NPvSF9EFa"

const endpoint = "https://eu.i.posthog.com"

const installIDKey = "errorreport:installId"

// KeyValueStore keeps the install ID between restarts (the kv cache).
type KeyValueStore interface {
	Get(ctx context.Context, key string) (string, error)
	Set(ctx context.Context, key string, value any, ttl time.Duration) error
}

type reporter struct {
	client    posthog.Client
	installID string
	enabled   func() bool
}

var (
	mutex   sync.RWMutex
	current *reporter
)

// Enabled is false when ERROR_REPORTING=false or the build has no token.
func Enabled() bool {
	if ProjectToken == "" {
		return false
	}
	return !strings.EqualFold(os.Getenv("ERROR_REPORTING"), "false")
}

// Start connects to PostHog; enabled is asked before every report, so the
// setting takes effect without a restart.
func Start(ctx context.Context, store KeyValueStore, enabled func() bool) {
	if !Enabled() {
		return
	}
	client, err := posthog.NewWithConfig(ProjectToken, posthog.Config{Endpoint: endpoint})
	if err != nil {
		log.Printf("error reporting: %v", err)
		return
	}
	mutex.Lock()
	current = &reporter{client: client, installID: installID(ctx, store), enabled: enabled}
	mutex.Unlock()
}

// Close sends what is still queued.
func Close() {
	active := activeReporter()
	if active == nil {
		return
	}
	_ = active.client.Close()
}

// installID is a random ID per install, so reports of one install group together.
func installID(ctx context.Context, store KeyValueStore) string {
	stored, err := store.Get(ctx, installIDKey)
	if err == nil && stored != "" {
		return stored
	}
	bytes := make([]byte, 16)
	_, _ = rand.Read(bytes)
	created := hex.EncodeToString(bytes)
	if err := store.Set(ctx, installIDKey, created, 0); err != nil {
		log.Printf("error reporting: saving install id: %v", err)
	}
	return created
}

func activeReporter() *reporter {
	mutex.RLock()
	defer mutex.RUnlock()
	if current == nil || !current.enabled() {
		return nil
	}
	return current
}

// Capture reports err; kind names where it happened (e.g. "plugin.streams")
// and becomes the issue title, properties add what helps to reproduce it.
func Capture(err error, kind string, properties map[string]any) {
	active := activeReporter()
	if active == nil || err == nil {
		return
	}
	exception := posthog.NewDefaultException(time.Now(), active.installID, kind, err.Error())
	exception.Properties = eventProperties(kind, properties)
	if enqueueErr := active.client.Enqueue(exception); enqueueErr != nil {
		log.Printf("error reporting: %v", enqueueErr)
	}
}

func eventProperties(kind string, properties map[string]any) posthog.Properties {
	result := posthog.NewProperties().
		Set("kind", kind).
		Set("$os", runtime.GOOS).
		Set("arch", runtime.GOARCH).
		Set("revision", buildRevision()).
		Set("$process_person_profile", false)
	for name, value := range properties {
		result.Set(name, value)
	}
	return result
}

// buildRevision is the git commit the binary was built from, when Go recorded it.
func buildRevision() string {
	info, found := debug.ReadBuildInfo()
	if !found {
		return ""
	}
	for _, setting := range info.Settings {
		if setting.Key == "vcs.revision" {
			return setting.Value
		}
	}
	return ""
}

// CapturePanic reports a recovered panic value.
func CapturePanic(recovered any, kind string, properties map[string]any) {
	Capture(fmt.Errorf("panic: %v", recovered), kind, properties)
}

// Recover reports panics in HTTP handlers, then panics again so net/http
// handles them as before.
func Recover(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		defer func() {
			recovered := recover()
			if recovered == nil {
				return
			}
			// ErrAbortHandler is how handlers end a response on purpose.
			if recovered != http.ErrAbortHandler {
				CapturePanic(recovered, "http.panic", map[string]any{"method": request.Method, "path": request.URL.Path})
			}
			panic(recovered)
		}()
		next.ServeHTTP(writer, request)
	})
}
