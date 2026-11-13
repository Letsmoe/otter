package plugins

import (
	"context"
	"errors"

	"github.com/Letsmoe/otter/internal/errorreport"
)

// ReportFailure sends a plugin failure to error tracking unless it is the
// site's fault (see IsExpectedFailure). properties say what was asked.
func ReportFailure(ctx context.Context, err error, plugin *Plugin, capability Capability, properties map[string]any) {
	if err == nil || IsExpectedFailure(err) {
		return
	}
	reported := map[string]any{
		"plugin":         plugin.Meta.Name,
		"plugin_version": plugin.Meta.Version,
		"capability":     string(capability),
	}
	var pluginError *PluginError
	if errors.As(err, &pluginError) {
		reported["plugin_error_code"] = pluginError.Code
	}
	for name, value := range properties {
		reported[name] = value
	}
	errorreport.Capture(ctx, err, "plugin."+string(capability)+": "+plugin.Meta.Name, reported)
}
