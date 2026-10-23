package plugins

import (
	"errors"
	"fmt"
	"testing"
)

func TestIsDeadLink(t *testing.T) {
	dead := &PluginError{Plugin: "resolvers", Message: "Video not found", Code: ErrorCodeDeadLink}
	if !IsDeadLink(fmt.Errorf("resolve: %w", dead)) {
		t.Error("wrapped dead_link not recognized")
	}
	offline := &PluginError{Plugin: "resolvers", Message: "HTTP 502", Code: ErrorCodeSourceOffline}
	if IsDeadLink(offline) || IsDeadLink(errors.New("timeout")) || IsDeadLink(nil) {
		t.Error("non-dead error recognized as dead_link")
	}
}
