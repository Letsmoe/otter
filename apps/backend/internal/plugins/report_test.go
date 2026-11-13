package plugins

import (
	"context"
	"errors"
	"fmt"
	"testing"
)

func TestOnlyPluginFaultsAreReported(t *testing.T) {
	expected := []error{
		&PluginError{Plugin: "site", Message: "captcha", Code: ErrorCodeBlocked},
		&PluginError{Plugin: "site", Message: "502", Code: ErrorCodeSourceOffline},
		&PluginError{Plugin: "site", Message: "slow down", Code: ErrorCodeRateLimited},
		&PluginError{Plugin: "hoster", Message: "file deleted", Code: ErrorCodeDeadLink},
		fmt.Errorf("run: %w", context.DeadlineExceeded),
	}
	for _, err := range expected {
		if !IsExpectedFailure(err) {
			t.Errorf("%v should not be reported", err)
		}
	}

	pluginFaults := []error{
		&PluginError{Plugin: "site", Message: "selector matched nothing", Code: ErrorCodeExtractionFailed},
		&PluginError{Plugin: "site", Message: "TypeError: x is undefined"},
		errors.New("plugin crashed"),
	}
	for _, err := range pluginFaults {
		if IsExpectedFailure(err) {
			t.Errorf("%v should be reported", err)
		}
	}
}
