package plugins

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"

	"go.opentelemetry.io/otel/attribute"

	"github.com/Letsmoe/otter/internal/tracing"
)

// PluginSettingsProvider supplies per-plugin env vars at invocation time.
type PluginSettingsProvider interface {
	PluginEnv(pluginName string) []string
}

// executor runs one plugin invocation in some JS runtime: the JSON request is
// the plugin's stdin, and whatever the plugin writes to stdout comes back.
type executor interface {
	run(ctx context.Context, p *Plugin, stdin []byte, env []string) ([]byte, error)
	// install fetches the plugin's dependencies, where the runtime needs that.
	install(ctx context.Context, dir string) error
}

// Runner invokes plugin capabilities. Every call runs in a fresh runtime —
// fully isolated, no shared state between calls.
type Runner struct {
	executor         executor
	envVars          []string // global env vars passed to every plugin invocation
	settingsProvider PluginSettingsProvider
	accountProvider  AccountProvider
}

// AccountProvider keeps the logins of plugins whose site needs an account.
type AccountProvider interface {
	HasAccount(pluginName string) bool
	// AccountEnv is the username and session, as env entries.
	AccountEnv(pluginName string) []string
	// Relogin logs in again with the saved password after the session expired.
	Relogin(ctx context.Context, p *Plugin) error
}

// SetAccountProvider wires in the saved plugin logins.
func (r *Runner) SetAccountProvider(p AccountProvider) {
	r.accountProvider = p
}

// NewEmbeddedRunner runs pre-bundled plugins in an in-process JS engine, for
// hosts without Bun (the phone app). See embedded.go.
func NewEmbeddedRunner() *Runner {
	return &Runner{executor: newEmbeddedExecutor()}
}

// SetEnv replaces the global env vars forwarded to all plugin subprocesses.
func (r *Runner) SetEnv(vars []string) {
	r.envVars = vars
}

// SetSettingsProvider wires in the per-plugin settings source.
func (r *Runner) SetSettingsProvider(p PluginSettingsProvider) {
	r.settingsProvider = p
}

type invocation struct {
	Capability string      `json:"capability"`
	Args       interface{} `json:"args,omitempty"`
}

type pluginResponse struct {
	Result json.RawMessage `json:"result,omitempty"`
	Error  string          `json:"error,omitempty"`
	// Code classifies the error: "blocked", "source_offline",
	// "extraction_failed", "rate_limited" or "dead_link". Plugins that don't
	// know their failure's kind leave it empty.
	Code string `json:"code,omitempty"`
}

// Error codes a plugin can attach to a failure.
const (
	ErrorCodeBlocked          = "blocked"
	ErrorCodeSourceOffline    = "source_offline"
	ErrorCodeExtractionFailed = "extraction_failed"
	ErrorCodeRateLimited      = "rate_limited"
	// ErrorCodeDeadLink: the hoster says the video is gone; the link never
	// plays again.
	ErrorCodeDeadLink = "dead_link"
	// ErrorCodeLoggedOut: the site no longer accepts the session (or there is
	// none); the backend logs in again and retries once.
	ErrorCodeLoggedOut = "logged_out"
	// ErrorCodeLoginFailed: the site refused the username or password.
	ErrorCodeLoginFailed = "login_failed"
)

// IsLoggedOut reports whether err is a plugin saying its session is gone.
func IsLoggedOut(err error) bool {
	var pluginError *PluginError
	if !errors.As(err, &pluginError) {
		return false
	}
	return pluginError.Code == ErrorCodeLoggedOut
}

// IsDeadLink reports whether err is a resolver saying the link's video is gone.
func IsDeadLink(err error) bool {
	var pluginError *PluginError
	if !errors.As(err, &pluginError) {
		return false
	}
	return pluginError.Code == ErrorCodeDeadLink
}

// IsExpectedFailure reports whether err is the site's fault rather than the
// plugin's: blocked, offline, rate limited, a dead link or a timeout.
func IsExpectedFailure(err error) bool {
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
		return true
	}
	var pluginError *PluginError
	if !errors.As(err, &pluginError) {
		return false
	}
	switch pluginError.Code {
	case ErrorCodeBlocked, ErrorCodeSourceOffline, ErrorCodeRateLimited, ErrorCodeDeadLink,
		ErrorCodeLoggedOut, ErrorCodeLoginFailed:
		return true
	}
	return false
}

// PluginError is a failure a plugin reported itself, with the optional code
// that says why. Callers match it with errors.As.
type PluginError struct {
	Plugin  string
	Message string
	Code    string
}

func (e *PluginError) Error() string {
	return fmt.Sprintf("plugin %q: %s", e.Plugin, e.Message)
}

func (r *Runner) invoke(ctx context.Context, p *Plugin, cap Capability, args interface{}) (json.RawMessage, error) {
	ctx, span := tracing.StartSpan(ctx, "plugin "+string(cap)+" "+p.Meta.Name,
		attribute.String("otter.plugin", p.Meta.Name),
		attribute.String("otter.plugin_version", p.Meta.Version),
		tracing.JSON("otter.plugin_args", traceableArgs(cap, args)),
	)
	defer span.End()
	result, err := r.invokeInSpan(ctx, p, cap, args)
	// A login's result is the session: it stays out of traces like the password.
	if err == nil && cap != CapabilityLogin {
		tracing.SetJSON(span, "otter.plugin_result", result)
	}
	if err == nil {
		return result, nil
	}
	span.RecordError(err)
	// A blocked or offline site is not the plugin's fault and doesn't send the trace.
	if !IsExpectedFailure(err) {
		tracing.Fail(span, err)
	}
	return nil, err
}

// traceableArgs is what a span may show of a call's arguments: a login's password is hidden.
func traceableArgs(cap Capability, args interface{}) interface{} {
	if cap != CapabilityLogin {
		return args
	}
	login, isLogin := args.(LoginArgs)
	if !isLogin {
		return "[hidden]"
	}
	return map[string]string{"username": login.Username, "password": "[hidden]"}
}

func (r *Runner) invokeInSpan(ctx context.Context, p *Plugin, cap Capability, args interface{}) (json.RawMessage, error) {
	stdout, err := r.execute(ctx, p, invocation{Capability: string(cap), Args: args})
	if err != nil {
		return nil, err
	}

	var resp pluginResponse
	if err := json.Unmarshal(stdout, &resp); err != nil {
		return nil, fmt.Errorf("plugin %q bad response: %w\nraw: %s", p.Meta.Name, err, stdout)
	}
	if resp.Error != "" {
		return nil, &PluginError{Plugin: p.Meta.Name, Message: resp.Error, Code: resp.Code}
	}
	return resp.Result, nil
}

// loadMeta invokes the plugin with the "meta" capability to read its metadata.
func (r *Runner) loadMeta(ctx context.Context, dir, entryPoint string) (PluginMeta, error) {
	stub := &Plugin{Dir: dir, EntryPoint: entryPoint, Meta: PluginMeta{Name: dir}}
	raw, err := r.invoke(ctx, stub, "meta", nil)
	if err != nil {
		return PluginMeta{}, err
	}
	var meta PluginMeta
	return meta, json.Unmarshal(raw, &meta)
}

// execute runs one invocation of the plugin with the global and per-plugin
// env vars, returning its raw stdout.
func (r *Runner) execute(ctx context.Context, p *Plugin, call invocation) ([]byte, error) {
	payload, err := json.Marshal(call)
	if err != nil {
		return nil, err
	}
	env := os.Environ()
	env = append(env, r.envVars...)
	if r.settingsProvider != nil {
		env = append(env, r.settingsProvider.PluginEnv(p.Meta.Name)...)
	}
	if r.accountProvider != nil && p.Meta.Account != nil {
		env = append(env, r.accountProvider.AccountEnv(p.Meta.Name)...)
	}
	return r.executor.run(ctx, p, payload, env)
}

// invokeWithAccount runs a call of a plugin that needs an account: without
// one it fails right away, and an expired session is renewed once.
func (r *Runner) invokeWithAccount(ctx context.Context, p *Plugin, cap Capability, args interface{}) (json.RawMessage, error) {
	if p.Meta.Account == nil || r.accountProvider == nil {
		return r.invoke(ctx, p, cap, args)
	}
	if !r.accountProvider.HasAccount(p.Meta.Name) {
		return nil, &PluginError{Plugin: p.Meta.Name, Message: "log in to this site in the plugin's settings", Code: ErrorCodeLoggedOut}
	}
	result, err := r.invoke(ctx, p, cap, args)
	if !IsLoggedOut(err) {
		return result, err
	}
	if err := r.accountProvider.Relogin(ctx, p); err != nil {
		return nil, err
	}
	return r.invoke(ctx, p, cap, args)
}

// Login hands the user's credentials to the plugin, which returns a session.
func (r *Runner) Login(ctx context.Context, p *Plugin, args LoginArgs) (*LoginResult, error) {
	if !p.Meta.Has(CapabilityLogin) {
		return nil, fmt.Errorf("plugin %q has no login", p.Meta.Name)
	}
	raw, err := r.invoke(ctx, p, CapabilityLogin, args)
	if err != nil {
		return nil, err
	}
	var result LoginResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, err
	}
	if result.Session == "" {
		return nil, fmt.Errorf("plugin %q returned no session", p.Meta.Name)
	}
	return &result, nil
}

// install fetches the plugin's dependencies inside its directory.
func (r *Runner) install(ctx context.Context, dir string) error {
	return r.executor.install(ctx, dir)
}

// Streams asks a plugin for the stream links it can find for one title.
func (r *Runner) Streams(ctx context.Context, p *Plugin, args StreamsArgs) (*StreamsResult, error) {
	if !p.Meta.Has(CapabilityStreams) {
		return nil, fmt.Errorf("plugin %q does not support streams", p.Meta.Name)
	}
	raw, err := r.invokeWithAccount(ctx, p, CapabilityStreams, args)
	if err != nil {
		return nil, err
	}
	var result StreamsResult
	return &result, json.Unmarshal(raw, &result)
}

// Subtitles asks a plugin for the caption files it can find for one title.
func (r *Runner) Subtitles(ctx context.Context, p *Plugin, args SubtitlesArgs) (*SubtitlesResult, error) {
	if !p.Meta.Has(CapabilitySubtitles) {
		return nil, fmt.Errorf("plugin %q does not support subtitles", p.Meta.Name)
	}
	raw, err := r.invokeWithAccount(ctx, p, CapabilitySubtitles, args)
	if err != nil {
		return nil, err
	}
	var result SubtitlesResult
	return &result, json.Unmarshal(raw, &result)
}

// Resolve turns a hoster link into a playable media URL.
func (r *Runner) Resolve(ctx context.Context, p *Plugin, url string) (*ResolveResult, error) {
	if !p.Meta.Has(CapabilityResolve) {
		return nil, fmt.Errorf("plugin %q does not support resolve", p.Meta.Name)
	}
	raw, err := r.invokeWithAccount(ctx, p, CapabilityResolve, ResolveArgs{URL: url})
	if err != nil {
		return nil, err
	}
	var result ResolveResult
	return &result, json.Unmarshal(raw, &result)
}
